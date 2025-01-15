package de.dnpm.ccdn.core


import scala.concurrent.{
  Future,
  ExecutionContext
}
import scala.util.Either
import cats.syntax.either._
import de.dnpm.ccdn.core.bfarm


final class FakeBfArMConnectorProvider extends bfarm.ConnectorProvider
{
  override def getInstance: bfarm.Connector = 
    FakeBfArMConnector
}


object FakeBfArMConnector extends bfarm.Connector
{
  def upload(
    report: bfarm.SubmissionReport
  )(
    implicit ec: ExecutionContext
  ): Future[Either[String,bfarm.SubmissionReport]] =
    Future.successful(
      report.asRight
    )
}
