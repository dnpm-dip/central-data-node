package de.dnpm.ccdn.core


import java.net.URI
import scala.util.chaining._
import cats.data.NonEmptyList
import de.dnpm.ccdn.core.bfarm.{
  DiagnosticType,
  Metadata,
  VitalStatus
}
import de.dnpm.ccdn.core.bfarm.Chromosome
import de.dnpm.ccdn.core.bfarm.onco._
import de.dnpm.dip.coding.{
  Code,
  Coding
}
import de.dnpm.dip.util.mapping.syntax._
import de.dnpm.dip.service.mvh
import de.dnpm.dip.model.{
  BaseVariant,
  ExternalId,
  Id,
  Medications,
}
import de.dnpm.dip.coding.atc.ATC
import de.dnpm.dip.model.FollowUp.PatientStatus
import PatientStatus.LostToFU
import de.dnpm.dip.mtb.model._



trait MTBMappings extends Mappings[MTBPatientRecord]
{

  override val useCase: mvh.UseCase.Value = mvh.UseCase.MTB


  protected implicit def extIdToCoding[T,S](extId: ExternalId[T,S]): Coding[S] =
    Coding(
      code = Code[S](extId.value),
      system = extId.system,
      display = None,
      version = None
    )


  protected implicit val medicationCodingToSubstance: Coding[Medications] => Substance =
    coding =>
      coding.system match { 
        case sys if sys == Coding.System[ATC].uri => CodedSubstance(coding)
        case _                                    => NamedSubstance(coding.display.getOrElse(coding.code.value))
      }


  protected implicit def oncoDiagnosis: MTBPatientRecord => OncologyCase.Diagnosis = {

    record =>

      val diagnoses = record.diagnoses.toList

      val (mainDiagnosis,otherDiagnoses) = 
        diagnoses.partition(_.`type`.latestBy(_.date).value.code.enumValue == MTBDiagnosis.Type.Main)
          .pipe { case (mains,others) => (mains.head,others) }

      val germlineDiagnoses =
        Option(
          diagnoses
            .flatMap(_.germlineCodes.getOrElse(Set.empty))
            .toSet
        )
        .filter(_.nonEmpty)

      val tumorStaging =
        mainDiagnosis.staging
          .map(_.latestBy(_.date))

      implicit val specimens = record.getSpecimens

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
        record.getPerformanceStatus.minByOption(_.effectiveDate)
          .map(_.value.code)
          .getOrElse(Code[ECOG.Value]("unknown")),
        germlineDiagnoses.isDefined,
        germlineDiagnoses,
        record.getHistologyReports.find(
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
        tumorStaging
          .flatMap(_.tnmClassification)
          .collect { 
            case TumorStaging.TNM(t,n,m) =>
              Set(t,n,m)
                .map(c => c.copy(display = c.display.orElse(Some(c.code.value))))
          },
        tumorStaging
          .flatMap(
            _.otherClassifications
             .map(_.map(c => KeyCoding(c.code,c.system)))
          ),
        performedSequencingType(record)
      )
  }

  protected implicit val priorDiagnostics: List[MolecularDiagnosticReport] => Option[OncologyCase.PriorDiagnostics] = {

    import DiagnosticType._           

    implicit val diagnosticType: MolecularDiagnosticReport.Type.Value => DiagnosticType.Value =
      Map(
        MolecularDiagnosticReport.Type.Array           -> Array,
        MolecularDiagnosticReport.Type.Single          -> Single,
        MolecularDiagnosticReport.Type.Karyotyping     -> Karyotyping,
        MolecularDiagnosticReport.Type.GenePanel       -> Panel,
        MolecularDiagnosticReport.Type.Panel           -> Panel,
        MolecularDiagnosticReport.Type.Exome           -> Exome,
        MolecularDiagnosticReport.Type.GenomeShortRead -> GenomeShortRead,
        MolecularDiagnosticReport.Type.GenomeLongRead  -> GenomeLongRead
      )
      .orElse {
        case _ => Other          
      }

    reports =>
      reports
        .maxByOption(_.issuedOn)
        .map(
          report => OncologyCase.PriorDiagnostics(
            report.`type`.code.enumValue.mapTo[DiagnosticType.Value],
            Some(report.issuedOn),
            // Simple and Copy Number Variants not represented in MTB-KDS PRIOR molecular diagnostics
            None,
            None
          )
        )
  }



  protected implicit val statusReason: MTBTherapy.StatusReason.Value => TerminationReason.Value = {

    import MTBTherapy.StatusReason._

    Map(
      PatientRefusal                       -> TerminationReason.V,
      PatientDeath                         -> TerminationReason.T,
      Progression                          -> TerminationReason.P,
      Toxicity                             -> TerminationReason.A,
      RegularCompletion                    -> TerminationReason.E,
      RegularCompletionWithDosageReduction -> TerminationReason.R,
      RegularCompletionWithSubstanceChange -> TerminationReason.W
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
        therapy.medication.map(_.mapAllTo[Substance]),
        therapy.statusReason.map(_.code.enumValue.mapTo[TerminationReason.Value]),
        responses
          .collectFirst { 
            case r if r.therapy.id == therapy.id => r.value
          }
          .collect { 
            case coding @ RECIST(code) if Set(PD,SD,PR,CR) contains code => coding.code
          }
      )

  }


  protected implicit val oncologyCase: mvh.Submission[MTBPatientRecord] => OncologyCase = {

    case mvh.Submission(record,metadata,_) =>

      implicit val responses = record.getResponses

      OncologyCase(
        record.mapTo[OncologyCase.Diagnosis],
        record.priorDiagnosticReports.getOrElse(List.empty).mapTo[Option[OncologyCase.PriorDiagnostics]],
        record.guidelineTherapies.map(
          _.mapAllTo[OncologyCase.PriorTherapy]
        )
      )

  }


  import OncologyMolecular._

  protected implicit val oncologyMolecular: List[SomaticNGSReport] => OncologyMolecular = {

    implicit val localizationMapping: Set[Coding[BaseVariant.Localization.Value]] => Localization.Value =
      _.collect { case BaseVariant.Localization(l) => l} match {
        case locs if locs contains BaseVariant.Localization.CodingRegion     => Localization.Coding
        case locs if locs contains BaseVariant.Localization.RegulatoryRegion => Localization.Regulatory
        case _                                                               => Localization.Neither
      }
  

    implicit val snvMapping: SNV => SmallVariant = 
      snv => SmallVariant(
        snv.id,
        GenomicSource.Somatic,
        snv.chromosome.mapTo[Chromosome.Value],
        snv.gene,
        snv.localization.getOrElse(Set.empty).mapTo[Localization.Value],
        snv.position.start,
        snv.position.end.getOrElse(snv.position.start),
        snv.refAllele.value,
        snv.altAllele.value,
        snv.dnaChange,
        snv.proteinChange,
        snv.transcriptId,
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
              Some(cnv.chromosome.mapTo[Chromosome.Value]),
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
        Option(reports.flatMap(_.results.simpleVariants.getOrElse(List.empty)).mapAllTo[SmallVariant]).filter(_.nonEmpty),
        Option(reports.flatMap(_.results.copyNumberVariants.getOrElse(List.empty).flatMap(_.mapTo[Set[CopyNumberVariant]]))).filter(_.nonEmpty),
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

      implicit val useType: UseType.Value => SystemicTherapyRecommendation.Type.Value =
        Map(
          UseType.InLabel       -> SystemicTherapyRecommendation.Type.InLabel, 
          UseType.OffLabel      -> SystemicTherapyRecommendation.Type.OffLabel,
          UseType.Compassionate -> SystemicTherapyRecommendation.Type.Compassionate,
          UseType.SecPreventive -> SystemicTherapyRecommendation.Type.SecPreventive,
          UseType.Unknown       -> SystemicTherapyRecommendation.Type.Unknown      
        )

      implicit val category: Set[MTBMedicationRecommendation.Category.Value] => SystemicTherapyRecommendation.Strategy.Value = {
         import MTBMedicationRecommendation.Category._
         Map(
           Set(CH,IM,ZS) -> SystemicTherapyRecommendation.Strategy.CIZ,
           Set(CH,IM)    -> SystemicTherapyRecommendation.Strategy.CI,
           Set(CH,ZS)    -> SystemicTherapyRecommendation.Strategy.CZ,
           Set(CH)       -> SystemicTherapyRecommendation.Strategy.CH,
           Set(HO)       -> SystemicTherapyRecommendation.Strategy.HO,
           Set(IM)       -> SystemicTherapyRecommendation.Strategy.IM,
           Set(ZS)       -> SystemicTherapyRecommendation.Strategy.ZS,
           Set(SZ)       -> SystemicTherapyRecommendation.Strategy.SZ,
         )
         .orElse {
           case _ => SystemicTherapyRecommendation.Strategy.SO
         }
      }
        
      recommendation =>
        OncologyPlan.SystemicTherapyRecommendation(
          recommendation.id,
          recommendation
            .useType
            .getOrElse(Coding(UseType.Unknown))
            .code.enumValue
            .mapTo[SystemicTherapyRecommendation.Type.Value],
          recommendation.medication.mapAllTo[Substance],
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
            .getOrElse(Set.empty)
            .collect { case MTBMedicationRecommendation.Category(value) => value }
            .mapTo[SystemicTherapyRecommendation.Strategy.Value]
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
 
        val studyRef = 
          recommendation.study.find(_.system == Coding.System[NCT].uri)
            .getOrElse(recommendation.study.head)
 
        StudyRecommendation(
          recommendation.id,
          studyRegisters(studyRef.system),
          studyRef.display.getOrElse("N/A") ,
          studyRef.id,
          recommendation.medication.map(_.mapAllTo[Substance]),
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
      _.filter(_.recommendationsMissingReason.isEmpty)
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

    implicit val standardRecist: PartialFunction[RECIST.Value,FollowUp.RECIST.Value] =
      Map(
        CR -> FollowUp.RECIST.CR,
        PR -> FollowUp.RECIST.PR,
        MR -> FollowUp.RECIST.PR,  // ???
        SD -> FollowUp.RECIST.SD,
        PD -> FollowUp.RECIST.PD,
      )


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
            therapy.statusReason.map(_.code.enumValue.mapTo[TerminationReason.Value]),
            therapy.medication,
            response.map(_.effectiveDate),
            response
              .map(_.value.code.enumValue)
              .collect(standardRecist)
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
         record.diagnoses.filter(
           _.`type`.latestBy(_.date).value.code.enumValue == MTBDiagnosis.Type.Metachronous
         )
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
               record.patient.vitalStatus.code.enumValue.mapTo[VitalStatus.Value],
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


  def apply(submission: mvh.Submission[MTBPatientRecord]): OncologySubmission = {

    OncologySubmission(
      submission.mapTo[Metadata],
      submission.mapTo[OncologyCase],
      submission.record.ngsReports
        .flatMap(
          reports =>
            Option.when(reports.nonEmpty)(reports.mapTo[OncologyMolecular])
        ),
      submission.record.getCarePlans.mapTo[Option[OncologyPlan]],
      submission.record.mapTo[Option[OncologyFollowUps]]
    )

  }

}

object MTBMappings extends MTBMappings
{
   override lazy val config = Config.instance
}
