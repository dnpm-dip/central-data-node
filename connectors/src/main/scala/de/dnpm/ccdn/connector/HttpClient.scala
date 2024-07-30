package de.dnpm.ccdn.connector


import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream._
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient


object HttpClient:

  lazy given system: ActorSystem =
    ActorSystem()

  lazy given materializer: Materializer =
    SystemMaterializer(system).materializer

  lazy val instance =
    StandaloneAhcWSClient()


