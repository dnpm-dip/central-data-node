package de.dnpm.ccdn.core


import scala.util.chaining._
import de.dnpm.ccdn.core.bfarm.SubmissionReport
import de.dnpm.ccdn.core.bfarm.onco._
import de.dnpm.ccdn.core.dip.UseCase
import de.dnpm.dip.coding.{
  Code,
  Coding
}
import de.dnpm.dip.util.mapping.syntax._
import de.dnpm.dip.service.mvh.MVHPatientRecord
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


  protected implicit val oncologyCase: MVHPatientRecord[MTBPatientRecord] => OncologyCase = {

    case MVHPatientRecord(record,metadata,_) =>

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


  protected implicit val oncoCarePlan: List[MTBCarePlan] => Option[OncologyPlan.CarePlan] =
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



  def apply(record: MVHPatientRecord[MTBPatientRecord]): SubmissionReport[OncologyCase,OncologyMolecular,OncologyPlan,OncologyFollowUps] =
    ???

}
