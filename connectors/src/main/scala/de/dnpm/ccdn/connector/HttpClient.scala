package de.dnpm.ccdn.connector


import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{
  Materializer,
  SystemMaterializer
}
import play.api.libs.ws.ahc.StandaloneAhcWSClient


object HttpClient
{

  lazy implicit val system: ActorSystem =
    ActorSystem()

  lazy implicit val materializer: Materializer =
    SystemMaterializer(system).materializer

  lazy val instance =
    StandaloneAhcWSClient()

}
