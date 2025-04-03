package de.dnpm.ccdn.core


import java.time.{
  LocalDate,
  YearMonth
}
import de.dnpm.ccdn.core.bfarm._
import de.dnpm.ccdn.core.dip.UseCase
import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.{
  CarePlan,
  Gender,
  Patient
}
import de.dnpm.dip.service.mvh
import de.dnpm.dip.util.mapping.syntax._
import shapeless.Witness


trait Mappings
{

  val config: Config

  val useCase: UseCase.Value


  implicit val useCaseMapping: UseCase.Value => Metadata.DiseaseType.Value =
    Map(
      UseCase.MTB -> bfarm.Metadata.DiseaseType.Oncological,
      UseCase.RD  -> bfarm.Metadata.DiseaseType.Rare
    )

  implicit def metadataMapping[CP <: CarePlan](
    implicit w: Witness.Aux[CP#StatusReason]
  ): (Patient,LocalDate,CP,mvh.Metadata) => Metadata = {

    case (patient,date,carePlan,metadata) =>
    
      import Metadata._

      implicit val nonInclusionReasonMapping: Coding[CP#StatusReason#Value] => RejectionJustification.Value = {
      
        val reason = w.value

        Map(
          Coding(reason.TargetedDiagnosticsRecommended)(reason.codeSystem) -> RejectionJustification.TargetDiagnosisRecommended,
          Coding(reason.Pyschosomatic)(reason.codeSystem)                  -> RejectionJustification.ProbablyPsychosomaticIllness,
          Coding(reason.NotRareDisease)(reason.codeSystem)                 -> RejectionJustification.ProbablyCommonDisease,
          Coding(reason.NonGeneticCause)(reason.codeSystem)                -> RejectionJustification.ProbablyNotGeneticCause,
          Coding(reason.Other)(reason.codeSystem)                          -> RejectionJustification.OtherReason
        )
      }

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

    Metadata(
      Submission(
        date,
        metadata.submissionType,
        config.submitterIds(patient.managingSite.get.code),
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
      Gender.unapply(patient.gender).get,  //TODO: improve, because unsafe 
      YearMonth.from(patient.birthDate),
      patient.address.municipalityCode,
      carePlan.statusReason.isEmpty,
      carePlan.issuedOn,
      carePlan.statusReason.map(_.mapTo[RejectionJustification.Value]),
    )
  }

}
