package de.dnpm.ccdn.connector


import java.time.LocalDate
import java.util.UUID.randomUUID
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._
import play.api.libs.json.{
  Json,
  OWrites
}
//import play.api.libs.ws.{
//  StandaloneWSClient => WSClient,
//}
import de.dnpm.dip.model.{
  HealthInsurance,
  Id,
  Site
}
import de.dnpm.dip.service.mvh.{
  Submission,
  TransferTAN
}
import de.dnpm.ccdn.core.bfarm.{
  CDN,
  LibraryType,
  SubmissionReport
}


class BfArMConnectorTests extends AsyncFlatSpec
{

  implicit val writesToken: OWrites[BfArMConnectorImpl.Token] =
    Json.writes[BfArMConnectorImpl.Token]

  val token =
    BfArMConnectorImpl.Token(
      access_token = "dummy",
      expires_in = 300,
      refresh_expires_in = 300,
      scope = "test",
      token_type = "Bearer"
    )

  def rndSubmissionReport: SubmissionReport =
    SubmissionReport(
      LocalDate.now,
      Submission.Type.Test,
      Id[TransferTAN](randomUUID.toString),
      Id[Site]("dummy"),
      Id[CDN]("dummy"),
      SubmissionReport.DiseaseType.Oncological,
      LibraryType.None,
      HealthInsurance.Type.UNK
    )


  val config =
    BfArMConnectorImpl.Config(
      "http://localhost/api/submission",
      "http://localhost/auth/token",
      "client-id",
      "changeit",
      Some(1)
    )


  // Test purpose: Ensure that the token fetch request is performed only once even on multiple uploads
  val tokenFetchCounter = new AtomicInteger(0)

  val wsclient = FakeWSClient {
    Map(
      config.authURL -> {
        case "POST" =>
          tokenFetchCounter.incrementAndGet
          (200,"OK",Some(Json.stringify(Json.toJson(token))))
      },
      config.apiURL -> {
        case "POST" => (200,"OK",None)
      }
    )
  }


  val connector =
    new BfArMConnectorImpl(
      config,
      wsclient
    )


  "API Token" must "have been fetched only once on multiple upload calls" in {

    val submissionReports = List.fill(20)(rndSubmissionReport)

    for {
      _ <- Future.traverse(submissionReports)(connector.upload)
    } yield tokenFetchCounter.get mustBe 1

  }

}
