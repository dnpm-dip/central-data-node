package de.dnpm.ccdn.core


import scala.concurrent.{
  Future,
  ExecutionContext
}


final class FakeBfArMConnectorProvider extends BfArM.ConnectorProvider:
  override def getInstance: BfArM.Connector = 
    FakeBfArMConnector


object FakeBfArMConnector extends BfArM.Connector:

  def upload(report: BfArM.SubmissionReport): Executable[BfArM.SubmissionReport] =
    Future.successful(
      report
    )

