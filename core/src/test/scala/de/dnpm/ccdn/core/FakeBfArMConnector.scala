package de.dnpm.ccdn.core


import scala.concurrent.{ExecutionContext, Future}
import cats.syntax.either._


final class FakeBfArMConnectorProvider extends bfarm.ConnectorProvider
{
  override def getInstance: bfarm.Connector = 
    FakeBfArMConnector()
}

object FakeBfArMConnector {
}

case class FakeBfArMConnector() extends bfarm.Connector
{

  def upload(report: bfarm.SubmissionReport)(implicit ec: ExecutionContext): Future[Either[String,Unit]] =
  {
    Future.successful(().asRight)
  }
}
