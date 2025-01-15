package de.dnpm.ccdn.core


import scala.concurrent.{
  Future,
  ExecutionContext
}
import scala.util.Either
import cats.syntax.either._


final class FakeBfArMConnectorProvider extends BfArM.ConnectorProvider
{
  override def getInstance: BfArM.Connector = 
    FakeBfArMConnector
}


object FakeBfArMConnector extends BfArM.Connector
{
  def upload(
    report: BfArM.SubmissionReport
  )(
    implicit ec: ExecutionContext
  ): Future[Either[String,BfArM.SubmissionReport]] =
    Future.successful(
      report.asRight
    )
}
