package de.dnpm.ccdn.core


import scala.util.chaining._
import java.time.YearMonth
import de.dnpm.ccdn.core.bfarm._
import de.dnpm.dip.model
import de.dnpm.dip.model.{
  Chromosome,
  CarePlan,
  ExternalId,
  Id,
  NGSReport,
  PatientRecord
}
import de.dnpm.dip.service.mvh
import de.dnpm.dip.service.mvh.{
  Submission,
  UseCase
}
import de.dnpm.dip.util.mapping.syntax._


trait Mappings[RecordType <: PatientRecord]
{

  val config: Config

  val useCase: UseCase.Value


  protected implicit def stringToId[T](id: String): Id[T] =
    Id(id)

  protected implicit def extIdToId[T,U](id: ExternalId[T,U]): Id[T] =
    id.value

  protected implicit def idToOther[T,U](id: Id[T]): Id[U] =
    id.asInstanceOf[Id[U]]


  protected implicit val useCaseMapping: UseCase.Value => Metadata.DiseaseType.Value =
    Map(
      UseCase.MTB -> bfarm.Metadata.DiseaseType.Oncological,
      UseCase.RD  -> bfarm.Metadata.DiseaseType.Rare
    )

  protected implicit val chromosomeMapping: Chromosome.Value => bfarm.Chromosome.Value =
    _.toString.replace("chr","")
     .pipe(bfarm.Chromosome.withName)


  protected implicit val sequencingType: NGSReport.Type.Value => SequencingType.Value =
    Map(
      NGSReport.Type.GenomeLongRead  -> SequencingType.WGSLr,
      NGSReport.Type.GenomeShortRead -> SequencingType.WGS,
      NGSReport.Type.Exome           -> SequencingType.WES, 
      NGSReport.Type.Panel           -> SequencingType.Panel
    )
    .orElse {
      case _ => SequencingType.None
    }


  protected implicit val nonInclusionReasonMapping: CarePlan.NoSequencingPerformedReason.Value => Metadata.RejectionJustification.Value = {
    import CarePlan.NoSequencingPerformedReason._

    Map(
      TargetedDiagnosticsRecommended -> Metadata.RejectionJustification.TargetDiagnosisRecommended,
      Pyschosomatic                  -> Metadata.RejectionJustification.ProbablyPsychosomaticIllness,
      NotRareDisease                 -> Metadata.RejectionJustification.ProbablyCommonDisease,
      NonGeneticCause                -> Metadata.RejectionJustification.ProbablyNotGeneticCause,
      Other                          -> Metadata.RejectionJustification.OtherReason
    )
  }


  import mvh.extensions._

  protected def performedSequencingType(record: RecordType): SequencingType.Value =
    record.mvhSequencingReports
      .maxByOption(_.issuedOn)
      .map(_.`type`.code.enumValue.mapTo[SequencingType.Value])
      .getOrElse(SequencingType.None)


  protected implicit val alternativeMetadataMapping: Submission[RecordType] => Metadata = {

    case Submission(record,metadata,datetime) =>
    
      import Metadata._


      implicit val consentProvisionType: mvh.Consent.Provision.Type.Value => MVConsent.Scope.Type.Value =
        Map(
          mvh.Consent.Provision.Type.Permit -> MVConsent.Scope.Type.Permit,
          mvh.Consent.Provision.Type.Deny   -> MVConsent.Scope.Type.Deny  
        )     

      implicit val consentPurpose: mvh.ModelProjectConsent.Purpose.Value => MVConsent.Scope.Domain.Value =
        Map(
          mvh.ModelProjectConsent.Purpose.Sequencing         -> MVConsent.Scope.Domain.MvSequencing,
          mvh.ModelProjectConsent.Purpose.Reidentification   -> MVConsent.Scope.Domain.ReIdentification,
          mvh.ModelProjectConsent.Purpose.CaseIdentification -> MVConsent.Scope.Domain.CaseIdentification
        )


    val mvhCarePlan = record.getCarePlans.minBy(_.issuedOn)  // minBy safe here, because validation ensures non-empty careplan list vor MVH submissions 

    Metadata(
      Metadata.Submission(
        datetime.toLocalDate,
        metadata.`type`,
        config.submitterId(record.patient.managingSite.get.code),
        config.dataNodeIds(useCase),
        Option.when(performedSequencingType(record) != SequencingType.None)(
          config.gdcId(record.patient.managingSite.get.code)
        ),
        useCase.mapTo[DiseaseType.Value]
      ),
      record.patient.healthInsurance.`type`.code,
      Metadata.MVConsent(
        metadata.modelProjectConsent.date,
        metadata.modelProjectConsent.version,
        metadata.modelProjectConsent.provisions.map(
          p => MVConsent.Scope(
            p.date,
            p.purpose.mapTo[MVConsent.Scope.Domain.Value],
            p.`type`.mapTo[MVConsent.Scope.Type.Value]
          )
        )
      ),
      metadata.researchConsents.map(
        _.map(
          bc => ResearchConsent(
            "2025.0.1",
            bc.date,
            bc.value
          )
        )
      ),
      metadata.transferTAN,
      None,  // Don't transmit Patient.id (for now)
      record.patient.gender.code,
      YearMonth.from(record.patient.birthDate),
      record.patient.address.map(_.municipalityCode.value).getOrElse(""),
      mvhCarePlan.noSequencingPerformedReason.isEmpty,
      mvhCarePlan.issuedOn,
      mvhCarePlan.noSequencingPerformedReason.map(_.code.enumValue.mapTo[RejectionJustification.Value]),
    )
  }


  implicit val vitalStatus: model.VitalStatus.Value => VitalStatus.Value =
    Map(
      model.VitalStatus.Alive    -> VitalStatus.Living,
      model.VitalStatus.Deceased -> VitalStatus.Deceased
    )

}
