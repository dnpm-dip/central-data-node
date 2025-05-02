package de.dnpm.ccdn.core


import java.net.URI
import java.time.YearMonth
import cats.data.NonEmptyList
import de.dnpm.ccdn.core.bfarm.{
  DiagnosticType,
  VitalStatus
}
import de.dnpm.ccdn.core.bfarm.rd._
import de.dnpm.ccdn.core.dip.UseCase
import de.dnpm.dip.coding.Coding
import de.dnpm.dip.util.mapping.syntax._
import de.dnpm.dip.model.{
  BaseVariant,
  NGSReport
}
import de.dnpm.dip.rd.model._
import RDCase.{
  Diagnosis, 
  PriorRD
}


trait RDMappings extends Mappings
{

  override val useCase: UseCase.Value = UseCase.RD


  protected implicit val diagnosisExtent: RDDiagnosis.FamilyControlLevel.Value => RDCase.Diagnosis.Extent.Value =
    Map(
      RDDiagnosis.FamilyControlLevel.Single -> Diagnosis.Extent.SingleGenome,
      RDDiagnosis.FamilyControlLevel.Duo    -> Diagnosis.Extent.DuoGenome,
      RDDiagnosis.FamilyControlLevel.Trio   -> Diagnosis.Extent.TrioGenome
    )

  protected implicit val diagnosisStatus: RDDiagnosis.VerificationStatus.Value => RDCase.Diagnosis.Status.Value =
    Map(
      RDDiagnosis.VerificationStatus.Confirmed   -> Diagnosis.Status.ConfirmedGeneticDiagnosis,
      RDDiagnosis.VerificationStatus.Partial     -> Diagnosis.Status.PartialGeneticDiagnosis,
      RDDiagnosis.VerificationStatus.Provisional -> Diagnosis.Status.SuspectedGeneticDiagnosis,
      RDDiagnosis.VerificationStatus.Unconfirmed -> Diagnosis.Status.NoGeneticDiagnosis
    )

  protected implicit val diagnosisMapping: (
    (
      NonEmptyList[RDDiagnosis],
      NonEmptyList[HPOTerm],
      List[RDCarePlan],
      List[GMFCSStatus]
    )
  ) => RDCase.Diagnosis = {

    case (diagnoses,hpoTerms,carePlans,gmfcs) => 

      RDCase.Diagnosis(
        hpoTerms.map(_.value),
        hpoTerms.head.value.version.get,
        diagnoses.toList.map(_.onsetDate).min,
        carePlans.map(_.issuedOn)
          .minOption
          .getOrElse(
            diagnoses.toList.map(_.recordedOn).min
          ),
        diagnoses
          .toList
          .map(_.familyControlLevel)
          .collect { case RDDiagnosis.FamilyControlLevel(v) => v } 
          .max
          .mapTo[Diagnosis.Extent.Value],
        Option(
          diagnoses.toList
            .map(_.verificationStatus)  
            .collect { case RDDiagnosis.VerificationStatus(v) => v } 
            .max
            .mapTo[Diagnosis.Status.Value]
        ),
        diagnoses.flatMap(_.codes),
        Option.when(diagnoses.exists(_.missingCodeReason.isDefined))(true),
        gmfcs.minByOption(_.effectiveDate).map(_.value.code)
      )
  }


  import RDNGSReport.Conclusion._

  implicit val disgnosticAssessment: RDNGSReport.Conclusion.Value => PriorRD.DiagnosticAssessment.Value =
    Map(
      PartialPhenotype                       -> PriorRD.DiagnosticAssessment.PhenotypeNotCompletelyClarified,
      StructuralVariantWithUnclearBreakpoint -> PriorRD.DiagnosticAssessment.StructuralVariantWithUnclearBreakpoint,
      UnclearVariantInDisease                -> PriorRD.DiagnosticAssessment.UnclearVariantInGeneticDisease,
      NoPathogenicVariantDetected            -> PriorRD.DiagnosticAssessment.NoPathogenicVariantDetected,
    )
    .orElse(
      _ => PriorRD.DiagnosticAssessment.Other
    )

   implicit val diagnosticType: NGSReport.Type.Value => DiagnosticType.Value =
     Map(
       NGSReport.Type.Array           -> DiagnosticType.Array,
       NGSReport.Type.Single          -> DiagnosticType.Single,
       NGSReport.Type.Karyotyping     -> DiagnosticType.Karyotyping,
       NGSReport.Type.Panel           -> DiagnosticType.Panel,
       NGSReport.Type.Exome           -> DiagnosticType.Exome,
       NGSReport.Type.GenomeShortRead -> DiagnosticType.GenomeShortRead,
       NGSReport.Type.GenomeLongRead  -> DiagnosticType.GenomeLongRead,
       NGSReport.Type.Other           -> DiagnosticType.Other
     )
   

  import Hospitalization.{ 
    NumberOfStays,
    NumberOfDays
  }

  import PriorRD.Hospitalizations
  import PriorRD.HospitalizationDays


  implicit val hospitalizations: NumberOfStays.Value => Hospitalizations.Value = 
    Map(
      NumberOfStays.Zero            -> Hospitalizations.Zero,
      NumberOfStays.UpToFive        -> Hospitalizations.UpToFive,
      NumberOfStays.UpToTen         -> Hospitalizations.UpToTen,
      NumberOfStays.UpToFifteen     -> Hospitalizations.UpToFifteen,
      NumberOfStays.OverFifteen     -> Hospitalizations.MoreThanFifteen,
      NumberOfStays.Unknown         -> Hospitalizations.Unknown
    )

  implicit val hospitalizationDays: NumberOfDays.Value => HospitalizationDays.Value = 
    Map(
      NumberOfDays.Zero          -> HospitalizationDays.Zero,
      NumberOfDays.UpToFive      -> HospitalizationDays.UpToFive,
      NumberOfDays.UpToFifteen   -> HospitalizationDays.UpToFifteen,
      NumberOfDays.UpToFifty     -> HospitalizationDays.UpToFifty,
      NumberOfDays.OverFifty     -> HospitalizationDays.MoreThanFifty,
      NumberOfDays.Unknown       -> HospitalizationDays.Unknown
    )


  protected implicit val priorRD: (
   (
     List[RDNGSReport],
     Option[Hospitalization],
     NonEmptyList[RDEpisodeOfCare]
   )
  ) => PriorRD = { 

    case (ngsReports,hospitalization,episodes) =>

      val priorDiagnostics = 
        ngsReports.find(_.conclusion.isDefined)

      PriorRD(
        priorDiagnostics
          .map(_.`type`.mapTo[DiagnosticType.Value])
          .getOrElse(DiagnosticType.NonePerformed),
        priorDiagnostics.map(_.issuedOn),
        priorDiagnostics
          .flatMap(_.conclusion)
          .map(_.mapTo[PriorRD.DiagnosticAssessment.Value])
          .getOrElse(PriorRD.DiagnosticAssessment.Other),
        hospitalization
          .map(_.numberOfStays.mapTo[Hospitalizations.Value])
          .getOrElse(Hospitalizations.Unknown),
        hospitalization
          .map(_.numberOfDays.mapTo[HospitalizationDays.Value])
          .getOrElse(HospitalizationDays.Unknown),
        YearMonth.from(
          episodes.toList
            .map(_.period.start)
            .min
        )
      )

  }

  protected implicit val rdCaseMapping: RDPatientRecord => RDCase = {
    record =>

      RDCase(
        Option(
          (
            record.diagnoses,
            record.hpoTerms,
            record.carePlans.getOrElse(List.empty),
            record.gmfcsStatus.getOrElse(List.empty)
          )
          .mapTo[RDCase.Diagnosis]
        ),
        Option(
          (
            record.getNgsReports,
            record.hospitalization,
            record.episodesOfCare
          )
          .mapTo[RDCase.PriorRD]
        )
      )
  }


  protected implicit val rdMolecular: List[RDNGSReport] => RDMolecular = {

    import RDMolecular.Variant.{
      Localization,
      Zygosity,
      ModeOfInheritance,
      SegregationAnalysis
    }
 
    implicit val localizationMapping: Set[Coding[BaseVariant.Localization.Value]] => Localization.Value =
     _.collect { case BaseVariant.Localization(l) => l } match {
 
       case locs if locs contains BaseVariant.Localization.CodingRegion     => Localization.CodingRegion
       case locs if locs contains BaseVariant.Localization.SplicingRegion   => Localization.SplicingRegion
       case locs if locs contains BaseVariant.Localization.RegulatoryRegion => Localization.RegulatoryRegion
       case _                                                               => Localization.IntronicIntergenic
     }
 
    implicit val zygosity: Variant.Zygosity.Value => Zygosity.Value =
      Map(
        Variant.Zygosity.Heterozygous  -> Zygosity.Heterozygous,
        Variant.Zygosity.Homozygous    -> Zygosity.Homozygous, 
        Variant.Zygosity.CompHet       -> Zygosity.CompHet,
        Variant.Zygosity.Hemi          -> Zygosity.Hemi,
        Variant.Zygosity.Homoplasmic   -> Zygosity.Homoplasmic,
        Variant.Zygosity.Heteroplasmic -> Zygosity.Heteroplasmic
      )
 
    implicit val segregationAnalysis: Variant.SegregationAnalysis.Value => SegregationAnalysis.Value =
      Map(
        Variant.SegregationAnalysis.NotPerformed -> SegregationAnalysis.NotPerformed,
        Variant.SegregationAnalysis.DeNovo       -> SegregationAnalysis.DeNovo,
        Variant.SegregationAnalysis.FromFather   -> SegregationAnalysis.FromFather,
        Variant.SegregationAnalysis.FromMother   -> SegregationAnalysis.FromMother
      )
 
    implicit val modeOfInheritance: Variant.ModeOfInheritance.Value => ModeOfInheritance.Value =
      Map(
        Variant.ModeOfInheritance.Dominant      -> ModeOfInheritance.Dominant, 
        Variant.ModeOfInheritance.Recessive     -> ModeOfInheritance.Recessive,
        Variant.ModeOfInheritance.Mitochondrial -> ModeOfInheritance.Mitochondrial,
        Variant.ModeOfInheritance.Xlinked       -> ModeOfInheritance.Xlinked,
        Variant.ModeOfInheritance.Unclear       -> ModeOfInheritance.Unclear
      )
 
    implicit val acmgCriterion: ACMG.Criterion => RDMolecular.ACMGCriterion =
      crit => RDMolecular.ACMGCriterion(
        crit.value.mapTo[ACMG.Criterion.Type.Value],
        crit.modifier.map(_.mapTo[ACMG.Criterion.Modifier.Value])
      )
 
    implicit val smallVariantMapping: SmallVariant => RDMolecular.SmallVariant =
      sv => RDMolecular.SmallVariant(
        sv.id,
        sv.genes,
        sv.chromosome.mapTo[Chromosome.Value],
        sv.position,
        sv.ref,
        sv.alt,
        sv.localization.map(_.mapTo[Localization.Value]),
        sv.cDNAChange,
        sv.gDNAChange,
        sv.proteinChange,
        sv.acmgClass.mapTo[ACMG.Class.Value],
        sv.acmgCriteria.map(_.mapAllTo[RDMolecular.ACMGCriterion]),
        sv.zygosity.mapTo[Variant.Zygosity.Value],
        sv.segregationAnalysis.map(_.mapTo[SegregationAnalysis.Value]),
        sv.modeOfInheritance.map(_.mapTo[ModeOfInheritance.Value]),
        sv.significance.map(_.mapTo[Variant.Significance.Value]),
        sv.externalIds.flatMap(_.headOption).map(_.value),
        sv.publications.map(_.map(_.id))
      )
 
    implicit val structuralVariantMapping: StructuralVariant => RDMolecular.StructuralVariant =
      sv => RDMolecular.StructuralVariant(
        sv.id,
        sv.genes,
        sv.localization.map(_.mapTo[Localization.Value]),
        sv.cDNAChange,
        sv.gDNAChange,
        sv.proteinChange,
        sv.iscnDescription,
        sv.acmgClass.mapTo[ACMG.Class.Value],
        sv.acmgCriteria.map(_.mapAllTo[RDMolecular.ACMGCriterion]),
        sv.zygosity.mapTo[Variant.Zygosity.Value],
        sv.segregationAnalysis.map(_.mapTo[SegregationAnalysis.Value]),
        sv.modeOfInheritance.map(_.mapTo[ModeOfInheritance.Value]),
        sv.significance.map(_.mapTo[Variant.Significance.Value]),
        sv.externalIds.flatMap(_.headOption).map(_.value),
        sv.publications.map(_.map(_.id))
      )
 
    implicit val copyNumberVariantMapping: CopyNumberVariant => RDMolecular.CopyNumberVariant =
      cnv => RDMolecular.CopyNumberVariant(
        cnv.id,
        cnv.genes,
        cnv.chromosome.mapTo[Chromosome.Value],
        cnv.startPosition,
        cnv.endPosition,
        cnv.`type`.mapTo[CopyNumberVariant.Type.Value],  
        cnv.localization.map(_.mapTo[Localization.Value]),
        cnv.cDNAChange,
        cnv.gDNAChange,
        cnv.proteinChange,
        cnv.acmgClass.mapTo[ACMG.Class.Value],
        cnv.acmgCriteria.map(_.mapAllTo[RDMolecular.ACMGCriterion]),
        cnv.zygosity.mapTo[Variant.Zygosity.Value],
        cnv.segregationAnalysis.map(_.mapTo[SegregationAnalysis.Value]),
        cnv.modeOfInheritance.map(_.mapTo[ModeOfInheritance.Value]),
        cnv.significance.map(_.mapTo[Variant.Significance.Value]),
        cnv.externalIds.flatMap(_.headOption).map(_.value),
        cnv.publications.map(_.map(_.id))
      )


    reports => 

      val smallVariants =
        reports.flatMap(_.results.flatMap(_.smallVariants).getOrElse(List.empty))

      val structuralVariants =
        reports.flatMap(_.results.flatMap(_.structuralVariants).getOrElse(List.empty))

      val copyNumberVariants =
        reports.flatMap(_.results.flatMap(_.copyNumberVariants).getOrElse(List.empty))

      RDMolecular(
        Option.when(smallVariants.nonEmpty)(smallVariants.mapAllTo[RDMolecular.SmallVariant]),
        Option.when(structuralVariants.nonEmpty)(structuralVariants.mapAllTo[RDMolecular.StructuralVariant]),
        Option.when(copyNumberVariants.nonEmpty)(copyNumberVariants.mapAllTo[RDMolecular.CopyNumberVariant]),
      )
    
  }


  implicit val clinicalManagementType: ClinicalManagementRecommendation.Type.Value => RDPlan.CarePlan.ClinicalManagementDescription.Value =
    Map(
      ClinicalManagementRecommendation.Type.DiseaseSpecificAmbulatoryCare -> RDPlan.CarePlan.ClinicalManagementDescription.DiseaseSpecificAmbulatoryCare,
      ClinicalManagementRecommendation.Type.UniversityAmbulatoryCare      -> RDPlan.CarePlan.ClinicalManagementDescription.UniversityAmbulatoryCare,
      ClinicalManagementRecommendation.Type.OtherAmbulatoryCare           -> RDPlan.CarePlan.ClinicalManagementDescription.OtherAmbulatoryCare,
      ClinicalManagementRecommendation.Type.LocalCRD                      -> RDPlan.CarePlan.ClinicalManagementDescription.LocalCrd, 
      ClinicalManagementRecommendation.Type.OtherCRD                      -> RDPlan.CarePlan.ClinicalManagementDescription.OtherCrd, 
      ClinicalManagementRecommendation.Type.GP                            -> RDPlan.CarePlan.ClinicalManagementDescription.GP,
      ClinicalManagementRecommendation.Type.Specialist                    -> RDPlan.CarePlan.ClinicalManagementDescription.Specialist
    )


  protected implicit val carePlan: RDCarePlan => RDPlan = {

     import de.dnpm.dip.model.Study.Registries._

     val studyRegisters: URI => String =
        Map(
          Coding.System[NCT].uri     -> "NCT",
          Coding.System[DRKS].uri    -> "DRKS",
          Coding.System[EudraCT].uri -> "EudraCT",
        )
        .orElse {
          case _ => "other"
        }

    implicit val studyRecommendation: RDStudyEnrollmentRecommendation => RDPlan.StudyRecommendation = {
      rec => 
    
        val study =
          rec.study.find(_.system == Coding.System[NCT].uri)
            .getOrElse(rec.study.head)
    
        RDPlan.StudyRecommendation(
          rec.id,
          studyRegisters(study.system),
          study.display.getOrElse("N/A"),
          study.id,
          rec.supportingVariants.map(_.map(_.variant.id))
        )
    }
    
    implicit val therapyRecommendation: RDTherapyRecommendation => RDPlan.TherapyRecommendation = {
   
      import RDPlan.TherapyRecommendation.Strategy._

      implicit val recommendationStrategy: RDTherapy.Type.Value => RDPlan.TherapyRecommendation.Strategy.Value =
        Map(
          RDTherapy.Type.SystemicMedication   -> SystemicMedication,
          RDTherapy.Type.TargetedMedication   -> TargetedMedication, 
          RDTherapy.Type.PreventionMedication -> PreventionMedication,
          RDTherapy.Type.Genetic              -> Genetic,
          RDTherapy.Type.Prophylactic         -> Prophylactic,
          RDTherapy.Type.EarlyDetection       -> EarlyDetection,
          RDTherapy.Type.Combination          -> Combination,
          RDTherapy.Type.Nutrition            -> Nutrition,
          RDTherapy.Type.Other                -> Other
        )
    
      rec => RDPlan.TherapyRecommendation(
        rec.id,
        rec.category.code,
        rec.`type`.mapTo[RDPlan.TherapyRecommendation.Strategy.Value],
        None,
        rec.supportingVariants.map(_.map(_.variant.id))
      )
    }

    carePlan => RDPlan(
      Some(
        RDPlan.CarePlan(
          carePlan.issuedOn,
          carePlan.studyEnrollmentRecommendations.exists(_.nonEmpty),
          carePlan.geneticCounselingRecommended.exists(_ == true),
          carePlan.reevaluationRecommended.exists(_ == true),
          carePlan.therapyRecommendations.exists(_.nonEmpty),
          carePlan.clinicalManagementRecommendation.isDefined,
          carePlan.clinicalManagementRecommendation
            .map(_.`type`.mapTo[RDPlan.CarePlan.ClinicalManagementDescription.Value])
            .map(Set(_))
        )
      ),
      carePlan.studyEnrollmentRecommendations.map(_.mapAllTo[RDPlan.StudyRecommendation]),
      carePlan.therapyRecommendations.map(_.mapAllTo[RDPlan.TherapyRecommendation])
    )

  }


  protected implicit val rdFollowUp: RDPatientRecord => Option[RDFollowUp] = {

    import RDDiagnosis.VerificationStatus.{
      Confirmed,
      Partial
    }

    implicit val hpoTermStatus: HPOTerm.Status.Value => RDFollowUp.PhenotypeChange.Value =
      Map(
        HPOTerm.Status.Unchanged -> RDFollowUp.PhenotypeChange.Unchanged,
        HPOTerm.Status.Improved  -> RDFollowUp.PhenotypeChange.Improved,
        HPOTerm.Status.Degraded  -> RDFollowUp.PhenotypeChange.Degraded,
        HPOTerm.Status.Abated    -> RDFollowUp.PhenotypeChange.NoLongerObserved
      )

    record =>
      for {
        
        fu <- record.followUps.flatMap(_.maxByOption(_.date))
        
        gmfcs = record.gmfcsStatus.flatMap(_.find(_.effectiveDate isBefore fu.date))

      } yield RDFollowUp(
        fu.date,
        Option(
          record.hpoTerms.map(
            hpo => hpo.status.map(_.latestBy(_.date)) match {
              case Some(st) =>
                RDFollowUp.PhenotypeChange(
                  hpo.value,
                  st.status.mapTo[RDFollowUp.PhenotypeChange.Value]
                )
              case None =>
                RDFollowUp.PhenotypeChange(
                  hpo.value,
                  hpo.recordedOn match {
                    case Some(d) if d.isEqual(fu.date) || d.isAfter(fu.date) =>
                      RDFollowUp.PhenotypeChange.NewlyAdded

                    case _ =>
                      RDFollowUp.PhenotypeChange.Unchanged
                  }
                )
            }
          )
          .toList
        ),
        gmfcs.map(_.value.code),
        record.diagnoses
          .map(_.verificationStatus.mapTo[RDDiagnosis.VerificationStatus.Value])
          .exists(s => s == Confirmed || s == Partial),
        record.diagnoses.head.notes.flatMap(_.lastOption),
        record.patient.vitalStatus.mapTo[VitalStatus.Value],
        record.patient.dateOfDeath
      )
  }


/*
  def apply(submission: Submission[RDPatientRecord]): SubmissionReport[RDCase,RDMolecular,RDPlan,RDFollowUps] = {

    val Submission(record,metadata,dateTime) = submission   

    val patient = record.patient
    val mvhCarePlan = record.getCarePlans.minByOption(_.issuedOn).get

    SubmissionReport(
      (patient,dateTime.toLocalDate,mvhCarePlan,metadata).mapTo[Metadata],
      submission.mapTo[RDCase],
      record.getNgsReports
        .pipe(
          reports =>
            Option.when(reports.nonEmpty)(reports.mapTo[RDMolecular])
        ),
      record.getCarePlans.mapTo[Option[RDPlan]],
      record.mapTo[Option[RDFollowUps]]
    )

  }
*/  
}
