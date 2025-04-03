package de.dnpm.ccdn.core


import de.dnpm.ccdn.core.bfarm.SubmissionReport
import de.dnpm.ccdn.core.bfarm.onco._
import de.dnpm.ccdn.core.dip.UseCase
//import de.dnpm.dip.coding.Coding
//import de.dnpm.dip.util.mapping.syntax._
import de.dnpm.dip.service.mvh.MVHPatientRecord
import de.dnpm.dip.mtb.model._


trait MTBMappings extends Mappings
{

  override val useCase: UseCase.Value = UseCase.MTB

/*
  implicit val oncologyCase: (MTBDiagnosis,List[PerformanceStatus]) => OncologyCase = {

???
  }
*/

  def apply(record: MVHPatientRecord[MTBPatientRecord]): SubmissionReport[OncologyCase,OncologyMolecular,OncologyPlan,OncologyFollowUps] =
    ???

}
