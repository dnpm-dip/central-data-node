package de.dnpm.ccdn.core.bfarm.onco


import java.time.LocalDate
import de.dnpm.dip.coding.{
  Code,
  Coding,
  SequenceOntology
}
import de.dnpm.dip.coding.hgnc.HGNC
import de.dnpm.dip.coding.hgvs.HGVS
import de.dnpm.dip.coding.icd.{
  ICD10GM,
  ICDO3
}
import de.dnpm.dip.mtb.model.{
  ECOG,
  OBDSGrading,
  MTBTherapy,
  MTBSystemicTherapy,
  RECIST,
  TumorStaging
}
import play.api.libs.json.{
  Json,
  Format,
  OFormat,
  OWrites,
  Reads
}


final case class CodingWithDate[T]
(
  coding: Coding[T],
  date: LocalDate
)

object CodingWithDate
{

  implicit def reads[T: Coding.System]: Reads[CodingWithDate[T]] =
    Reads {
      js => 
        for {
          coding <- js.validate[Coding[T]]
          date <- (js \ "date").validate[LocalDate]
        } yield CodingWithDate(
          coding,
          date
        )
    }

  implicit def writes[T]: OWrites[CodingWithDate[T]] =
    OWrites {
      cwd => Json.toJsObject(cwd.coding) + ("date" -> Json.toJson(cwd.date))
    }
}


//object Therapy
//{
  object TerminationReason extends Enumeration
  {
    val E, R, W, A, P, S, V, T, U = Value

    implicit val format: Format[Value] =
      Json.formatEnum(this)
  }
//}


final case class OncologyCase
(
  diagnosisOd: OncologyCase.Diagnosis,
  priorDiagnostic: Option[OncologyCase.PriorDiagnostics],
  priorProcedures: Option[List[OncologyCase.PriorTherapy]]
)


object OncologyCase
{

  final case class Diagnosis
  (
    mainDiagnosis: CodingWithDate[ICD10GM],
    additionalDiagnoses: Option[Set[CodingWithDate[Any]]],
    ecogPerformanceStatusScore: Code[ECOG.Value],
    germlineDiagnosisConfirmed: Boolean,
    germlineDiagnoses: Option[Coding[ICD10GM]],
    histology: Coding[ICDO3.M],
    topography: Coding[ICDO3.T],
    grading: Code[Any],
//    tnmClassifications: Option[Set[Coding[Any]]],  
//    additionalClassification: Option[Coding[Any]]
    tnmClassifications: Option[Set[Coding[TumorStaging.TNM.Systems]]],
    additionalClassification: Option[Coding[TumorStaging.OtherSystems]]
  )

  object Diagnosis
  {
    implicit val format: OFormat[Diagnosis] =
      Json.format[Diagnosis]
  }


  final case class PriorDiagnostics
  (
    `type`: PriorDiagnostics.Type.Value,
    date: Option[LocalDate],
    simpleVariants: Option[List[PriorDiagnostics.SimpleVariant]],
    complexVariants: Option[List[String]]
  )

  object PriorDiagnostics
  {

    object Type extends Enumeration
    {
 
      val Array           = Value("array")
      val Single          = Value("single")
      val Karyotyping     = Value("karyotyping")
      val Panel           = Value("panel")
      val Exome           = Value("exome")
      val GenomeShortRead = Value("genomeShortRead")
      val GenomeLongRead  = Value("genomeLongRead")
      val Other           = Value("other")
      val None            = Value("none")
 
      implicit val format: Format[Value] =
        Json.formatEnum(this)
  
    }

    final case class SimpleVariant
    (
      gene: Coding[HGNC],
      transcript: Coding[Any],
      dnaChange: Coding[HGVS],
      proteinChange: Option[Coding[HGVS]],
      variantTypes: Option[List[Coding[SequenceOntology]]]
    )


    implicit val formatSimpleVariant: OFormat[SimpleVariant] =
      Json.format[SimpleVariant]
  
    implicit val format: OFormat[PriorDiagnostics] =
      Json.format[PriorDiagnostics]
  }


  final case class PriorTherapy
  (
    treatmentType: Code[MTBSystemicTherapy.Category.Value],
    intention: Option[Code[MTBTherapy.Intention.Value]],
    therapyStartDate: Option[LocalDate],
    therapyEndDate: Option[LocalDate],
    substances: Option[Set[Coding[Any]]],
    terminationReasonOBDS: Option[TerminationReason.Value],
    therapyResponse: Option[Code[RECIST.Value]]
  )

  object PriorTherapy
  { 
    implicit val format: OFormat[PriorTherapy] =
      Json.format[PriorTherapy]
  }

  implicit val format: OFormat[OncologyCase] =
    Json.format[OncologyCase]
  
}
