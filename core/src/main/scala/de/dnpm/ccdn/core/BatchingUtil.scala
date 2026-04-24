package de.dnpm.ccdn.core


import scala.collection.IterableOps
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._


trait BatchingUtil
{

  /**
   * Traverse a Collection C[_] with an effectful transformation T => F[U] 
   * in successive (i.e. monadically sequenced) batches.
   * Monadic chaining ensures the batches are always processed sequentially, 
   * even in case of a "false" effect like scala.concurrent.Future[T],
   * which is evaluated eagerly.
   *
   * @param ts Collection of T elements
   * @param batchSize Batch size into which to split [[ts]]
   * @param f Effectful tranformation: T => F[U] 
   * @return Collection C[U] of transformation results wrapped in F[_] 
   */
  def batchTraverse[T,C[x] <: IterableOps[x,C,C[x]],F[_]: cats.Monad,U](
    ts: C[T],
    batchSize: Int
  )(
    f: T => F[U]
  ): F[C[U]] =
    ts.grouped(batchSize)
      .foldLeft(
        ts.iterableFactory.empty[U].pure // Load empty accumulator into Monad
      )(
        (acc,batch) =>
          for { 
            results <- acc
            batchResults <- batch.toList traverse f
          } yield results ++ batchResults
      )

}
