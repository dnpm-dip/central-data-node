package de.dnpm.ccdn.core


import scala.concurrent.{ExecutionContext, Future, Promise}
import cats.syntax.either._

import java.util.concurrent.atomic.AtomicReference


final class FakeBfArMConnectorProvider extends bfarm.ConnectorProvider
{
  override def getInstance: bfarm.Connector = 
    FakeBfArMConnector(false)
}


case class FakeBfArMConnector(uploadsTakeTime:Boolean) extends bfarm.Connector
{
  val uploadFinishTimings = new AtomicReference(List[Long]())


  def upload(report: bfarm.SubmissionReport)(implicit ec: ExecutionContext): Future[Either[String,Unit]] =
  {
    val p=Promise[Either[String,Unit]]()
    if (uploadsTakeTime) {
      p.success({
        Thread.sleep(100)
        uploadFinishTimings.updateAndGet(old => System.currentTimeMillis() :: old)
        ()
      }.asRight)
    } else{
      uploadFinishTimings.updateAndGet(old => System.currentTimeMillis()::old)
      Future.successful(().asRight)
    }
    p.future
  }
}
