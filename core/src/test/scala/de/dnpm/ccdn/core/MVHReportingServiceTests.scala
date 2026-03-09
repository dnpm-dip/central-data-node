package de.dnpm.ccdn.core


import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._



final class MVHReportingServiceTests extends AsyncFlatSpec with BeforeAndAfterEach
{
  behavior of "MVHReportingService"


  private case class TestFixture() {
    val service = MVHReportingService.service
    val fakeDipConnector:FakeDIPConnector = service.dipConnector.asInstanceOf[FakeDIPConnector]
    val fakeBfarmConnector:FakeBfArMConnector = service.bfarmConnector.asInstanceOf[FakeBfArMConnector]
  }
  private val fixture = TestFixture()



  it must "handle multiple uploads from every DIP node in one go" in {
    fixture.fakeDipConnector.nSubmissions = 4
    fixture.fakeBfarmConnector.uploadsTakeTime = false

    for {
      
      _ <- fixture.service.pollReports
      
      _ = fixture.service.queue.entries(_ => true) must not be (empty)

      _ <- fixture.service.uploadReports

      _ <- fixture.service.confirmSubmissions
      
    } yield fixture.service.queue.entries(_ => true) must be (empty)
  }

  it must "(non-deterministic) not process more submissions simultaneously than it has threads" in {
    //configure bfarmconnecteor to halt for 100 msec during upload
    fixture.fakeDipConnector.nSubmissions = 1
    fixture.fakeBfarmConnector.uploadsTakeTime = true

    // testing setup has 39 clinic-usecases, so there can be no less than 39 uploads to run
    val expectedNumReports = Config.instance.sites.flatMap(it => it._2.useCases).size
    //have more reports overall than nThreads
    assert(expectedNumReports > MVHReportingService.nThreads)
    //but no more than twice as many, so that a boolean predicate works to split one set of timings in two
    assert(expectedNumReports <= MVHReportingService.nThreads*2)

    //run
    for{

      _ <- fixture.service.pollReports

      _ <- fixture.service.uploadReports

      _ <- fixture.service.confirmSubmissions

    } yield{
      val allUploadTimings = fixture.fakeBfarmConnector.uploadFinishTimings.get()
      //expect that theres a contingent of uploads that all happened within 25msec of the first
      val minTiming = allUploadTimings.min
      val maxTimeDifference = 25 //generous
      def firstUploadBatchPredicate:Long=>Boolean = _ - minTiming < maxTimeDifference
      val firstUploadBatch = allUploadTimings.filter(firstUploadBatchPredicate)
      val remainingUploads = allUploadTimings.filter(!firstUploadBatchPredicate(_))

      /*for(t <- allUploadTimings.sorted){
        println(t)
      }*/

      //verifying basics
      assertResult(expectedNumReports)(allUploadTimings.size)
      assertResult(expectedNumReports)(firstUploadBatch.size+remainingUploads.size)

      //verify that the first contingent has exactly <nThreads> items
      assertResult(MVHReportingService.nThreads)(firstUploadBatch.size)
      //verify that the rest is equally close
      val minRemainderTiming = remainingUploads.min
      assert(remainingUploads.forall(_ - minRemainderTiming < maxTimeDifference))
    }
  }

  override def afterEach() = {
    fixture.fakeBfarmConnector.uploadFinishTimings.set(List[Long]())
  }

}
