package de.dnpm.ccdn.connector


import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream._
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient


object HttpClient:

  private lazy implicit val system: ActorSystem =
    ActorSystem()

  private lazy implicit val materializer: Materializer =
    SystemMaterializer(system).materializer

  lazy val instance =
    StandaloneAhcWSClient()


