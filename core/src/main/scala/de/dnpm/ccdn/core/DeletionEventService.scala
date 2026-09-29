package de.dnpm.ccdn.core


import cats.syntax.either._
import de.dnpm.ccdn.core.dip.DipConnector
import de.dnpm.dip.coding.Code
import de.dnpm.dip.model.Site
import de.dnpm.dip.service.mvh.MVHService.DeletionEvent
import de.dnpm.dip.service.mvh.UseCase
import de.dnpm.dip.util.Logging

import java.time.{Clock, LocalDateTime}
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future}


object DeletionEventService
{
  import scala.concurrent.ExecutionContext.Implicits.global

  private[core] lazy val instance =
    new DeletionEventService(
      Config.instance,
      dip.DipConnector.getInstance.get
    )
}


/**
 * Queries DIP nodes for [[DeletionEvent]]s, i.e. notifications that a patient's
 * data (and thus all of their submissions) was deleted at the source site.
 *
 * For every site and UseCase, the point in time of the last successful query is kept, so
 * that subsequent requests only ask for events that occurred since then. A
 * site that has never been queried (e.g. right after service startup) is
 * queried without a time filter, returning its complete deletion history.
 */
class DeletionEventService
(
  config: Config,
  dipConnector: DipConnector
)(
  implicit ec: ExecutionContext
)
extends Logging
{

  /**
   * Serves local time. Abstracted for the purpose of unit tests
   */
  private[core] var clock: Clock = Clock.systemDefaultZone()

  // Per-site-and-UseCase high-water mark of the last successful query. Intentionally
  // kept in memory only: losing it on restart just means the next query re-fetches
  // the complete history, which is explicitly acceptable.
  private val lastQueried = new ConcurrentHashMap[(Code[Site],UseCase.Value),LocalDateTime]

  /**
   * Queries all [[DeletionEvent]]s of the given site and UseCase. Only on success
   * is the last-queried timestamp advanced, so that a failed attempt is simply
   * retried (with the same or wider time window) on the next call instead of
   * silently losing events.
   */
  def deletionEvents(site: Code[Site], useCase: UseCase.Value): Future[Either[String,Seq[DeletionEvent]]] = {

    val since = Option(lastQueried.get(site -> useCase))
    val queriedAt = LocalDateTime.now(clock)

    dipConnector.deletionEvents(site,useCase,since)
      .map {
        case Left(err) =>
          log.error(s"Problem querying $useCase DeletionEvents of site $site: $err")
          err.asLeft

        case Right(events) =>
          lastQueried.put(site -> useCase,queriedAt)
          events.asRight
      }
  }

}
