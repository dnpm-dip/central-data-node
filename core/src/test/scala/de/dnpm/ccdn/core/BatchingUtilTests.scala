package de.dnpm.ccdn.core


import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{
  ExecutionContext,
  Future
}
import scala.collection.concurrent.{
  Map,
  TrieMap
}
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers._
import org.scalatest.Inspectors._



class BatchingUtilTests extends AsyncFlatSpec with BatchingUtil
{

  // Suggested by coderabbit:
  // Use a multi-threaded ExecutionContext (EC) instead of AsyncFlatSpec's default serial EC,
  // to ensure that the test becomes a genuine regression guard.
  override implicit def executionContext: ExecutionContext =
    ExecutionContext.global


  "Batch processing" must "have occurred strictly sequentially" in { 

    val batchSize = 10

    // Set up 5 batches of size 'batchSize' containing decreasing delay times 50 ms, 40 ms, 30 ms ...
    // together with index/position of entries
    val delaysWithIdx: List[(Long,Int)] =
      List(50L,40L,30L,20L,10L)
        .flatMap(List.fill(batchSize)(_))
        .zipWithIndex

    val counter = new AtomicInteger

    // Map to track the execution order (given by 'counter') for a given index/position
    val executionOrder: Map[Int,Int] = TrieMap.empty

    // Simulate some asynchronous operation:
    // For a pair of delay and index, wait for the given delay and record the execution order
    val f: ((Long,Int)) => Future[Unit] = {
      case (delay,i) => Future {
        Thread.sleep(delay)
        executionOrder += i -> counter.incrementAndGet
        ()
      }
    }

    // By construction, the operations in early batches must run longer than those in later batches, given their longer delay.
    // So, to ensure that they haven't been executed all at once in parallel, check that for each pair
    // of consecutive batches the recorded execution order of entries in the later batch is always after those of the first batch
    for {
      _ <- batchTraverse(delaysWithIdx,batchSize)(f)

      (_,indices) = delaysWithIdx.unzip

    } yield forAll(indices.grouped(batchSize).sliding(2).toList){ 
      batchPair =>  
        forAll(batchPair.last){
          idx2 => forAll(batchPair.head){ idx1 => assert(executionOrder(idx1) < executionOrder(idx2)) }
        }
    }

  }

}
