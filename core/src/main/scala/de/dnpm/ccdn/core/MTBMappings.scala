package de.dnpm.ccdn.core


import java.net.URI
import scala.util.chaining._
import cats.data.NonEmptyList
import de.dnpm.ccdn.core.bfarm.{
  Metadata,
  SubmissionReport
}
import de.dnpm.ccdn.core.bfarm.onco._
import de.dnpm.ccdn.core.dip.UseCase
import de.dnpm.dip.coding.{
  Code,
  Coding
}
import de.dnpm.dip.util.mapping.syntax._
import de.dnpm.dip.service.mvh.Submission
import de.dnpm.dip.model.{
  BaseVariant,
  ExternalId,
  Id
}
import de.dnpm.dip.model.FollowUp.PatientStatus
import PatientStatus.LostToFU
import de.dnpm.dip.mtb.model._



trait MTBMappings extends Mappings
{

  override val useCase: UseCase.Value = UseCase.MTB


  protected implicit def oncoDiagnosis(
    implicit
    specimens: List[TumorSpecimen],
    histologyReports: List[HistologyReport]
  ): ((List[MTBDiagnosis],List[PerformanceStatus])) => OncologyCase.Diagnosis = {

    case (diagnoses,ecogs) =>

      val (mainDiagnosis,otherDiagnoses) = 
        diagnoses.partition {
          d =>
            val MTBDiagnosis.Type(t) = d.`type`.latestBy(_.date).value
            t == MTBDiagnosis.Type.Main
        }
        .pipe { case (mains,others) => (mains.head,others) }

      val germlineDiagnoses =
        Option(
          diagnoses
            .flatMap(_.germlineCodes.getOrElse(Set.empty))
            .toSet
        )
        .filter(_.nonEmpty)


      OncologyCase.Diagnosis(
        CodingWithDate(
          mainDiagnosis.code,
          mainDiagnosis.recordedOn
        ),
        Option.when(
          otherDiagnoses.nonEmpty
        )(
          otherDiagnoses.map(d => CodingWithDate(d.code,d.recordedOn)).toSet
        ),
        ecogs.minByOption(_.effectiveDate)
          .map(_.value.code)
          .getOrElse(Code[ECOG.Value]("unknown")),
        germlineDiagnoses.isDefined,
        germlineDiagnoses,
        histologyReports.find(
          _.specimen
           .resolve
           .flatMap(_.diagnosis.resolveOn(diagnoses))
           .exists(_.id == mainDiagnosis.id)
        )
        .map(_.results.tumorMorphology.value)
        .get,
        mainDiagnosis.topography,
        mainDiagnosis.grading
          .map(_.latestBy(_.date))
          .flatMap(
            _.codes.collectFirst {
              case c if c.system == Coding.System[OBDSGrading.Value].uri =>
                c.code.asInstanceOf[Code[OBDSGrading.Value]]
            }
          ),
        mainDiagnosis.staging
          .map(_.latestBy(_.date))
          .flatMap(_.tnmClassification)
          .collect { 
            case TumorStaging.TNM(t,n,m) => Set(t,n,m)
          },
        None  // ignore additional staging classifications
      )
  }

  protected implicit val priorDiagnostics: List[MolecularDiagnosticReport] => Option[OncologyCase.PriorDiagnostics] = {

    import OncologyCase.PriorDiagnostics.Type._           
      
    implicit val diagnosticType: Coding[MolecularDiagnosticReport.Type.Value] => OncologyCase.PriorDiagnostics.Type.Value =
      Map(
        Coding(MolecularDiagnosticReport.Type.Array)           -> Array,
        Coding(MolecularDiagnosticReport.Type.Single)          -> Single,
        Coding(MolecularDiagnosticReport.Type.Karyotyping)     -> Karyotyping,
        Coding(MolecularDiagnosticReport.Type.GenePanel)       -> Panel,
        Coding(MolecularDiagnosticReport.Type.Panel)           -> Panel,
        Coding(MolecularDiagnosticReport.Type.Exome)           -> Exome,
        Coding(MolecularDiagnosticReport.Type.GenomeShortRead) -> GenomeShortRead,
        Coding(MolecularDiagnosticReport.Type.GenomeLongRead)  -> GenomeLongRead
      )
      .orElse {
        case _ => Other          
      }

    reports =>
      reports
        .maxByOption(_.issuedOn)
        .map(
          report => OncologyCase.PriorDiagnostics(
            report.`type`.mapTo[OncologyCase.PriorDiagnostics.Type.Value],
            Some(report.issuedOn), // Simple and Copy Number Variants not represented in MTB-KDS PRIOR molecular diagnostics
            None,
            None
          )
        )
  }



  protected implicit val statusReason: Coding[MTBTherapy.StatusReason.Value] => TerminationReason.Value = {

    import MTBTherapy.StatusReason._

    Map(
      Coding(PatientRefusal)                       -> TerminationReason.V,
      Coding(PatientDeath)                         -> TerminationReason.T,
      Coding(Progression)                          -> TerminationReason.P,
      Coding(Toxicity)                             -> TerminationReason.A,
      Coding(RegularCompletion)                    -> TerminationReason.E,
      Coding(RegularCompletionWithDosageReduction) -> TerminationReason.R,
      Coding(RegularCompletionWithSubstanceChange) -> TerminationReason.W
    )
    .orElse { 
      _ => TerminationReason.S
    }
  }


  protected implicit def priorTherapy(
    implicit responses: List[Response]
  ): MTBSystemicTherapy => OncologyCase.PriorTherapy = {

    import RECIST._

    therapy =>
      OncologyCase.PriorTherapy(
        therapy.category.getOrElse(Coding(MTBSystemicTherapy.Category.S)).code,
        therapy.intent.map(_.code),
        therapy.period.map(_.start),
        therapy.period.flatMap(_.endOption),
        therapy.medication,
        therapy.statusReason.map(_.mapTo[TerminationReason.Value]),
        responses
          .collectFirst { 
            case r if r.therapy.id == therapy.id => r.value
          }
          .collect { 
            case coding @ RECIST(code) if Set(PD,SD,PR,CR) contains code => coding.code
          }
      )

  }


  protected implicit val oncologyCase: Submission[MTBPatientRecord] => OncologyCase = {

    case Submission(record,metadata,_) =>

      implicit val specimens = record.getSpecimens
      implicit val histologyReports = record.getHistologyReports
      implicit val responses = record.getResponses

      OncologyCase(
        (record.diagnoses.toList,record.getPerformanceStatus).mapTo[OncologyCase.Diagnosis],
        record.priorDiagnosticReports.getOrElse(List.empty).mapTo[Option[OncologyCase.PriorDiagnostics]],
        record.guidelineTherapies.map(
          _.mapAllTo[OncologyCase.PriorTherapy]
        )
      )

  }


  import OncologyMolecular._

  protected implicit def stringToId[T](id: String): Id[T] = 
    Id(id)

  protected implicit def extIdToId[T,U](id: ExternalId[T,U]): Id[T] = 
    id.value

  protected implicit def idToOther[T,U](id: Id[T]): Id[U] = 
    id.asInstanceOf[Id[U]]


  protected implicit val oncologyMolecular: List[SomaticNGSReport] => OncologyMolecular = {

    implicit val localizationMapping: Set[Coding[BaseVariant.Localization.Value]] => Localization.Value = {
      _.collect { case BaseVariant.Localization(l) => l} match {
  
        case locs if locs contains BaseVariant.Localization.CodingRegion     => Localization.Coding
        case locs if locs contains BaseVariant.Localization.RegulatoryRegion => Localization.Regulatory
        case _                                                               => Localization.Neither
      }
  
    }

    implicit val snvMapping: SNV => SmallVariant = 
      snv => SmallVariant(
        snv.id,
        GenomicSource.Somatic,
        snv.gene,
        snv.localization.getOrElse(Set.empty).mapTo[Localization.Value],
        snv.transcriptId,
        snv.dnaChange,
        snv.proteinChange,
        None,  // Not defind in MTB-KDS
        None   // Not defind in MTB-KDS
      )

    implicit val cnvMapping: CNV => Set[CopyNumberVariant] = {
      cnv => 
    
        import CNV.Type._
    
        val typ =
          cnv.`type` match {
            case CNV.Type(LowLevelGain)  => CopyNumberVariant.Type.LowLevelGain
            case CNV.Type(HighLevelGain) => CopyNumberVariant.Type.HighLevelGain
            case _                       => CopyNumberVariant.Type.Loss // Last case: Loss
          }
    
        val chr =
          Code[Chromosome.Value](cnv.chromosome.toString.replace("chr",""))
    
        val start = cnv.startRange.map(_.start)
        val end   = cnv.endRange.map(_.start)
    
        cnv.reportedAffectedGenes.getOrElse(Set.empty)
          .map(gene =>
            CopyNumberVariant(
              Id(s"${cnv.id.value}_${gene.display.getOrElse("")}"),
              GenomicSource.Somatic,
              Some(gene),
              cnv.localization.getOrElse(Set.empty).mapTo[Localization.Value],
              typ,
              Some(chr),
              start,
              end 
            )
          )
    
    }

    implicit val complexBiomarkerMapping: SomaticNGSReport => ComplexBiomarker = {
      report =>
    
        import HRDScore.Interpretation._
    
        // No differentiated interpretation for HRDSCore components LOH, LST, TAI,
        // so use the overall interpetation on HRDSCore object itself
        val hrdHigh =
          report.results
            .hrdScore
            .flatMap(_.interpretation)
            .collect { case HRDScore.Interpretation(High) => true }
    
        ComplexBiomarker(
          report.id,
          None,
          report.results.tmb.map(_.value.value),
          hrdHigh,
          hrdHigh,
          hrdHigh
        )
    
    }


    reports =>
      OncologyMolecular(
        Option(reports.flatMap(_.results.simpleVariants).mapAllTo[SmallVariant]).filter(_.nonEmpty),
        Option(reports.flatMap(_.results.copyNumberVariants.flatMap(_.mapTo[Set[CopyNumberVariant]]))).filter(_.nonEmpty),
        Option(reports.mapAllTo[ComplexBiomarker]).filter(_.nonEmpty)
      )

  }

  protected implicit val oncoCarePlan: List[MTBCarePlan] => Option[OncologyPlan] = {

    import OncologyPlan.{
      StudyRecommendation,
      SystemicTherapyRecommendation
    }
 
    implicit val therayRecommendation: MTBMedicationRecommendation => SystemicTherapyRecommendation = {
 
      import MTBMedicationRecommendation.UseType
 
      implicit val useType: Coding[UseType.Value] => SystemicTherapyRecommendation.Type.Value =
        Map(
          Coding(UseType.InLabel)       -> SystemicTherapyRecommendation.Type.InLabel, 
          Coding(UseType.OffLabel)      -> SystemicTherapyRecommendation.Type.OffLabel,
          Coding(UseType.Compassionate) -> SystemicTherapyRecommendation.Type.Compassionate,
          Coding(UseType.SecPreventive) -> SystemicTherapyRecommendation.Type.SecPreventive,
          Coding(UseType.Unknown)       -> SystemicTherapyRecommendation.Type.Unknown      
        )
 
      recommendation =>
        OncologyPlan.SystemicTherapyRecommendation(
          recommendation.id,
          recommendation
            .useType
            .getOrElse(Coding(UseType.Unknown))
            .mapTo[SystemicTherapyRecommendation.Type.Value],
          recommendation.medication,
          recommendation.levelOfEvidence
            .map(_.grading)
            .getOrElse(Coding(LevelOfEvidence.Grading.Undefined))
            .code,
          recommendation
            .levelOfEvidence
            .flatMap(_.addendums.map(_.map(_.code)))
            .getOrElse(Set.empty),
          recommendation.priority.code,
          recommendation.supportingVariants.map(_.map(_.variant.id)),
          recommendation
            .category
            .getOrElse(Coding(MTBMedicationRecommendation.Category.SO))
            .code,
        )
    }
 
    implicit val studyRecommendation: MTBStudyEnrollmentRecommendation => StudyRecommendation = {
 
      import de.dnpm.dip.model.Study.Registries._
 
      val studyRegisters: URI => String =
        Map(
          Coding.System[NCT].uri     -> "NCT",
          Coding.System[DRKS].uri    -> "DRKS",
          Coding.System[EudraCT].uri -> "Eudra-CT/CTIS",
        )
        .orElse {
          case _ => "other"
        }
 
      recommendation =>
 
        val studyRef = recommendation.study.head
 
        StudyRecommendation(
          recommendation.id,
          studyRegisters(studyRef.system),
          studyRef.display.getOrElse("N/A") ,
          studyRef.id,
          recommendation.medication,
          recommendation.levelOfEvidence
            .map(_.grading)
            .getOrElse(Coding(LevelOfEvidence.Grading.Undefined))
            .code,
          recommendation
            .levelOfEvidence
            .flatMap(_.addendums.map(_.map(_.code)))
            .getOrElse(Set.empty),
          recommendation.priority.code,
          recommendation.supportingVariants.map(_.map(_.variant.id))
        )
    }
 
    implicit val oncoCarePlan: List[MTBCarePlan] => Option[OncologyPlan.CarePlan] =
      _.filter(_.statusReason.isEmpty)
       .pipe {
         carePlans =>
           carePlans
             .minByOption(_.issuedOn)
             .map(_.issuedOn)
             .map(date =>
               OncologyPlan.CarePlan(
                 molecularBoardDecisionDate = date,
                 studyRecommended = carePlans.exists(_.studyEnrollmentRecommendations.exists(_.nonEmpty)),
                 counsellingRecommended = carePlans.exists(_.geneticCounselingRecommendation.isDefined),
                 reEvaluationRecommended = carePlans.exists(_.histologyReevaluationRequests.exists(_.nonEmpty)),
                 interventionRecommended = false,  // Not in MTB-KDS
                 suitableInterventions  = None,  // Not in MTB-KDS
                 otherRecommendations =
                   carePlans
                     .flatMap(_.procedureRecommendations.getOrElse(List.empty))
                     .map(_.code)
                     .pipe { 
                       case codings if codings.nonEmpty => Some(codings.map(_.code))
                       case _                           => None
                     }
               )
           )
       }
 
     carePlans => 
       Option.when(carePlans.nonEmpty)(
         OncologyPlan(
           carePlans.mapTo[Option[OncologyPlan.CarePlan]],
           carePlans.flatMap(_.medicationRecommendations.getOrElse(List.empty))
             .pipe(
                recs =>
                  Option.when(recs.nonEmpty)(recs.mapAllTo[SystemicTherapyRecommendation])
             ),
           carePlans.flatMap(_.studyEnrollmentRecommendations.getOrElse(List.empty))
             .pipe(
                recs =>
                  Option.when(recs.nonEmpty)(recs.mapAllTo[StudyRecommendation])
             )
         )
       )
 
  }


  protected implicit val followUpMapping: MTBPatientRecord => Option[OncologyFollowUps] = {
  
    import RECIST._

    implicit def therapiesMapping(
      implicit responses: List[Response]
    ): List[MTBSystemicTherapy] => List[FollowUp.Therapy] = 
      _.collect {
    
        case therapy if therapy.period.isDefined =>

          val response = 
            responses
              .filter(_.therapy.id == therapy.id)
              .maxByOption(_.effectiveDate)
         
          FollowUp.Therapy(
            therapy.id,
            therapy.period.get.start,
            therapy.period.flatMap(_.endOption),
            therapy.statusReason.map(_.mapTo[TerminationReason.Value]),
            therapy.medication,
            response.map(_.effectiveDate),
            response
              .map(_.value)
              .collect { 
                case coding @ RECIST(code) if Set(PD,SD,PR,CR) contains code => coding.code
              }
          )
    
      }

   record =>
     if (
       record.followUps.exists(fu =>
         fu.nonEmpty &&
         fu.maxBy(_.date).patientStatus.collect { case PatientStatus(LostToFU) => true }.isEmpty
       )
     ){
          
       implicit val responses = record.getResponses

       val metachronousTumors =
         record.diagnoses.filter {
           d =>
             val MTBDiagnosis.Type(t) = d.`type`.latestBy(_.date).value
             t == MTBDiagnosis.Type.Metachronous
         }

       Some(
         OncologyFollowUps(
           NonEmptyList.of(
             FollowUp(
               record.followUps.get.maxBy(_.date).date,
               metachronousTumors.nonEmpty,
               Option.when(metachronousTumors.nonEmpty)(
                 NonEmptyList.fromListUnsafe(
                   metachronousTumors.map(
                     diag => 
                       CodingWithDate(
                         diag.code,
                         diag.`type`.latestBy(_.date).date
                       )
                   )
                 )
               ),
               record.getPerformanceStatus.maxByOption(_.effectiveDate)
                 .map(_.value.code)
                 .getOrElse(Code[ECOG.Value]("unknown")),
               record.patient.vitalStatus.code,
               record.followUps
                 .getOrElse(List.empty)
                 .filter(
                   _.patientStatus.collect { case PatientStatus(LostToFU) => true }.isDefined
                 )
                 .maxByOption(_.date)
                 .map(_.date),
               record.patient.dateOfDeath,
               Option(
                 record.getSystemicTherapies.map(_.latest).mapTo[List[FollowUp.Therapy]]
               )
               .filter(_.nonEmpty)
             )
           )
         )
       )

     } else None
  }


  def apply(submission: Submission[MTBPatientRecord]): SubmissionReport[OncologyCase,OncologyMolecular,OncologyPlan,OncologyFollowUps] = {

    val Submission(record,metadata,dateTime) = submission   

    val patient = record.patient
    val mvhCarePlan = record.getCarePlans.minByOption(_.issuedOn).get

    SubmissionReport(
      (patient,dateTime.toLocalDate,mvhCarePlan,metadata).mapTo[Metadata],
      submission.mapTo[OncologyCase],
      record.getNgsReports
        .pipe(
          reports =>
            Option.when(reports.nonEmpty)(reports.mapTo[OncologyMolecular])
        ),
      record.getCarePlans.mapTo[Option[OncologyPlan]],
      record.mapTo[Option[OncologyFollowUps]]
    )

  }
}
