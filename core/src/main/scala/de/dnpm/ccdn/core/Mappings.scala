package de.dnpm.ccdn.core


import scala.util.chaining._
import java.time.{
  LocalDate,
  YearMonth
}
import de.dnpm.ccdn.core.bfarm._
import de.dnpm.dip.coding.{
  CodedEnum,
  Coding
}
import de.dnpm.dip.model
import de.dnpm.dip.model.{
  Chromosome,
  CarePlan,
  ExternalId,
  Gender,
  Id,
  Patient
}
import de.dnpm.dip.service.mvh
import de.dnpm.dip.service.mvh.{
  Submission,
  UseCase
}
import de.dnpm.dip.util.mapping.syntax._
import shapeless.Witness


trait Mappings
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

  protected implicit def enumCodingToValue[E <: CodedEnum](
    implicit w: Witness.Aux[E]
  ): Coding[E#Value] => E#Value =
    coding =>
      w.value.unapply(coding.code.value).get

  protected implicit def enumCodingToTargetValue[E <: CodedEnum,T](
    implicit
    w: Witness.Aux[E],
    f: E#Value => T
  ): Coding[E#Value] => T =
    coding =>
      f(w.value.unapply(coding.code.value).get)

/*
  protected implicit def chromosomeMapping[Chr <: Chromosome with Enumeration]: Chr#Value => bfarm.Chromosome.Value =
    _.toString.replace("chr","")
     .pipe(bfarm.Chromosome.withName)
*/

  protected implicit val chromosomeMapping: Chromosome.Value => bfarm.Chromosome.Value =
    _.toString.replace("chr","")
     .pipe(bfarm.Chromosome.withName)


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

  protected implicit def metadataMapping[CP <: CarePlan]: ((Patient,LocalDate,CP,Submission.Metadata)) => Metadata = {

    case (patient,date,carePlan,metadata) =>
    
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

    val Gender(gender) = patient.gender

    Metadata(
      Metadata.Submission(
        date,
        metadata.`type`,
        config.submitterId(patient.managingSite.get.code),
        config.gdcId(patient.managingSite.get.code),
        config.dataNodeIds(useCase),
        useCase.mapTo[DiseaseType.Value]
      ),
      patient.healthInsurance.`type`.code,
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
      gender,
      YearMonth.from(patient.birthDate),
      patient.address.municipalityCode,
      carePlan.noSequencingPerformedReason.isEmpty,
      carePlan.issuedOn,
      carePlan.noSequencingPerformedReason.map(_.mapTo[RejectionJustification.Value]),
    )
  }


  implicit val vitalStatus: model.VitalStatus.Value => VitalStatus.Value =
    Map(
      model.VitalStatus.Alive    -> VitalStatus.Living,
      model.VitalStatus.Deceased -> VitalStatus.Deceased
    )

}
