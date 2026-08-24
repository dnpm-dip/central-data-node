package de.dnpm.ccdn.core.dip

import de.dnpm.dip.coding.Coding
import de.dnpm.dip.model.{EpisodeOfCare, HealthInsurance, Id, NGSReport, Patient, Period, Site}
import de.dnpm.dip.service.mvh.Submission.{DiagnosticExtent, SequenceType, Type}
import de.dnpm.dip.service.mvh.{BroadConsent, Consent, JsonEnumKeyHelpers, TransferTAN, UseCase}
import play.api.libs.json.{Format, Json, OFormat}

import java.time.LocalDateTime


final case class Report(
     id: Id[TransferTAN],
    createdAt: LocalDateTime,
    patient: Id[Patient],
    episodeOfCare: Option[Id[EpisodeOfCare]],  // Optional for backwards compatibility. Default would be the patient's chronologically first EpisodeOfCare
    status: Report.Status.Value,
    site: Coding[Site],
    useCase: UseCase.Value,
    `type`: de.dnpm.dip.service.mvh.Submission.Type.Value,
    sequencingType: Option[NGSReport.Type.Value],
    diagnosticExtent: Option[DiagnosticExtent.Value], // For quarter report (appendix 1)
    sequenceTypes: Option[Set[SequenceType.Value]], // For quarter report (appendix 2)
    healthInsuranceType: HealthInsurance.Type.Value,
    consentStatus: Option[Map[Consent.Category.Value,Boolean]],      // For quarter report (appendix 2): Is the respective Consent given in the submission?
    consentRevocation: Option[Map[Consent.Category.Value,Boolean]] , // For quarter report (appendix 2): Has the respective Consent been revoked (compared to previous submission)?
    reasonResearchConsentMissing: Option[BroadConsent.ReasonMissing.Value])
object Report extends JsonEnumKeyHelpers
{

  object Status extends Enumeration
  {
    /**
     * 1st state. When a submission is freshly fetched from the DIP node,
     * not yet further processed
     */
    val Unsubmitted: Value = Value("unsubmitted")
    /**
     * 2nd state. When a submission is submitted to BfArM
     */
    val SubmittedToBfarm: Value = Value("submitted")
    /**
     * 3rd state. When submission to BfArM has been reported back to
     * the source DIP node.
     */
    val confirmedToSource: Value = Value("confirmed")
    /**
     * 4th state. Associated submission has been downloaded from source DIP
     * node, encrytped and stored.
     */
    val submissionBackedup: Value = Value("submissionbackedup")
    /**
     * 5th state. The report was also backed up.
     */
    val reportBackedup: Value = Value("reportbackedup")

    /**
     * 6th and final state. The report has been stored for quarter reports
     *
     * Subsequently, in this state a report may be removed
     */
    val reportArchived: Value = Value("archived")



    implicit val formatValue: Format[Value] =
      Json.formatEnum(this)
  }

  final case class Filter
  (
    period: Option[Period[LocalDateTime]] = None,
    status: Option[Set[Status.Value]] = None,
    `type`: Option[Set[Type.Value]] = None,
    patient: Option[Set[Id[Patient]]] = None
  )


  implicit val formatInsType: Format[HealthInsurance.Type.Value] =
    Json.formatEnum(HealthInsurance.Type)

  implicit val formatNgsType: Format[NGSReport.Type.Value] =
    Json.formatEnum(NGSReport.Type)

  implicit val format: OFormat[Report] =
    Json.format[Report]
}