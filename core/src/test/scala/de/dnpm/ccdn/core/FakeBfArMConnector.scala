package de.dnpm.ccdn.core


import scala.concurrent.{ExecutionContext, Future}
import cats.syntax.either._

import java.util.concurrent.atomic.AtomicReference


final class FakeBfArMConnectorProvider extends bfarm.ConnectorProvider
{
  override def getInstance: bfarm.Connector = 
    FakeBfArMConnector()
}

object FakeBfArMConnector {
  final val uploadDelayMsec = 100
}

case class FakeBfArMConnector() extends bfarm.Connector
{
  val uploadFinishTimings = new AtomicReference(List[Long]())
  var uploadsTakeTime:Boolean = false

  def upload(report: bfarm.SubmissionReport)(implicit ec: ExecutionContext): Future[Either[String,Unit]] =
  {
    if (uploadsTakeTime) {
      Future{
        Thread.sleep(FakeBfArMConnector.uploadDelayMsec)
        uploadFinishTimings.updateAndGet(old => System.currentTimeMillis()::old)
        ().asRight
      }
    } else {
      uploadFinishTimings.updateAndGet(old => System.currentTimeMillis()::old)
      Future.successful(().asRight)
    }
  }
}
