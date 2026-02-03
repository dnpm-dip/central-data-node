package de.dnpm.ccdn.test.uploader


import java.io.FileInputStream
import java.time.LocalDate
import java.util.UUID.randomUUID
import scala.util.{
  Success,
  Failure
}
import de.dnpm.dip.util.Logging
import de.dnpm.dip.model.{
  HealthInsurance,
  Id,
  Site
}
import de.dnpm.dip.service.mvh.Submission
import de.dnpm.dip.service.mvh.TransferTAN
import de.dnpm.ccdn.core.bfarm.{
  CDN,
  LibraryType,
  SubmissionReport
}
import de.dnpm.ccdn.connector.BfArMConnectorImpl
import play.api.libs.json.Json


object Uploader extends Logging
{

  import scala.concurrent.ExecutionContext.Implicits.global

  private def submissionReport =
    SubmissionReport(
      LocalDate.now,
      Submission.Type.Initial,
      Id[TransferTAN](randomUUID.toString),
      Id[Site]("260840108"),
      Id[CDN]("KDKTUE005"),
      SubmissionReport.DiseaseType.Oncological,
      LibraryType.WGSLr,
      HealthInsurance.Type.UNK,
    )

  private val connector =
    BfArMConnectorImpl.instance


  def main(args: Array[String]) : Unit = {

    val report =
      args.headOption
        .map(new FileInputStream(_))
        .map(Json.parse)
        .map(Json.fromJson[SubmissionReport](_))
        .map(_.get)
        .getOrElse(submissionReport)

    log.info(s"Uploading report: ${Json.prettyPrint(Json.toJson(report))}")    

    connector.upload(report)
      .onComplete {
        case Success(Right(_)) =>
          log.info("BfArM Report successfully uploaded")
          System.exit(0)

        case Success(Left(err)) =>
          log.error(err)
          System.exit(1)

        case Failure(t) =>
          log.error("An exception occurred",t)
          System.exit(1)
      }
/*    
      .foreach { 
        case Right(_)  =>
          log.info("BfArM Report successfully uploaded")
          System.exit(0)

        case Left(err) =>
          log.error(err)
          System.exit(1)
      }
*/    
  }

}
