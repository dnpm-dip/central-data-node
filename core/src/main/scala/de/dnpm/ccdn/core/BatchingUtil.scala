package de.dnpm.ccdn.core


import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._


trait BatchingUtil
{
  /**
   * Traverse a Seq[T] with an effectful transformation T => F[U] 
   * in successive (i.e. monadically sequenced) batches.
   * Monadic chaining ensures the batches are always processed sequentially, 
   * even in case of a "false" effect like scala.concurrent.Future[T],
   * which is evaluated eagerly.
   *
   * @param ts Sequence of T elements
   * @param batchSize Batch size into which to split [[ts]]
   * @param f Effectful tranformation: T => F[U] 
   * @return Sequence Seq[U] of transformation results wrapped in F[_] 
   */
  def batchTraverse[T,F[_]: cats.Monad,U](
    ts: Seq[T],
    batchSize: Int
  )(
    f: T => F[U]
  ): F[Seq[U]] =
    ts.grouped(batchSize)
      .foldLeft(
        Seq.empty[U].pure // Load empty accumulator into Monad
      )(
        (acc,batch) =>
          for { 
            results <- acc
            batchResults <- batch traverse f
          } yield results ++ batchResults
      )

}
