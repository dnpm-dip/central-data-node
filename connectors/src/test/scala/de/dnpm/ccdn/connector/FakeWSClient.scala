package de.dnpm.ccdn.connector


import java.net.URI
import scala.concurrent.Future
import play.api.libs.ws.{
  StandaloneWSClient => WSClient,
  StandaloneWSRequest => WSRequest,
  StandaloneWSResponse => WSResponse
}
//import play.api.libs.ws.InMemoryBody


object FakeWSClient
{

  final case class Response(
    uri: URI,
    optBody: Option[String],
    status: Int,
    statusText: String
  )
  extends WSResponse
  { 
    def body = optBody.getOrElse("")

    def bodyAsBytes = org.apache.pekko.util.ByteString.fromString(body)

    def bodyAsSource = ???

    def cookie(name: String): Option[play.api.libs.ws.WSCookie] = None

    def cookies: scala.collection.Seq[play.api.libs.ws.WSCookie] = Seq.empty

    def headers: Map[String,scala.collection.Seq[String]] = Map.empty

    def underlying[T]: T = ???

  }


  final case class Request(
    url: String,
    optBody: Option[play.api.libs.ws.WSBody],
    response: Response
  )
  extends WSRequest
  {

   type Self = FakeWSClient.Request
   type Response = FakeWSClient.Response


   def auth: Option[(String, String, play.api.libs.ws.WSAuthScheme)] = None

   def body = optBody.get

   def calc: Option[play.api.libs.ws.WSSignatureCalculator] = None

   def contentType: Option[String] = ???

   def cookies: Seq[play.api.libs.ws.WSCookie] = Seq.empty

   def delete(): Future[Response] = Future.successful(response)

   def execute(): Future[Response] = Future.successful(response)

   def execute(method: String): Future[Response] = Future.successful(response)

   def followRedirects: Option[Boolean] = None

   def get(): Future[Response] = Future.successful(response)

   def head(): Future[Response] = Future.successful(response)

   def headers: Map[String,Seq[String]] = Map.empty

   def method: String = ???

   def options(): Future[Response] = Future.successful(response)
   
   def patch[T](body: T)(
     implicit bw: play.api.libs.ws.BodyWritable[T]
   ): Future[Response] =
     Future.successful(response)

   def post[T](body: T)(
     implicit bw: play.api.libs.ws.BodyWritable[T]
   ): Future[Response] =
     Future.successful(response)

   def proxyServer: Option[play.api.libs.ws.WSProxyServer] = None

   def put[T](body: T)(
     implicit bw: play.api.libs.ws.BodyWritable[T]
   ): Future[Response] =
     Future.successful(response)

   def queryString: Map[String,Seq[String]] = Map.empty //TODO

   def requestTimeout: Option[scala.concurrent.duration.Duration] = None

   def sign(calc: play.api.libs.ws.WSSignatureCalculator): Self = this

   def stream(): Future[Response] = Future.successful(response)

   
   def uri = URI.create(url)

   def virtualHost: Option[String] = None

   def withAuth(
     username: String,
     password: String,
     scheme: play.api.libs.ws.WSAuthScheme
   ): Self = this
   
   def withBody[T](body: T)(implicit bw: play.api.libs.ws.BodyWritable[T]): Self = ???
//     copy(optBody = Some(bw(body)))
   
   def withCookies(cookies: play.api.libs.ws.WSCookie*): Self = this

   def withDisableUrlEncoding(disableUrlEncoding: Boolean): Self = this

   def withFollowRedirects(follow: Boolean): Self = this

   def withHttpHeaders(headers: (String, String)*): Self = this

   def withMethod(method: String): Self = this

   def withProxyServer(proxyServer: play.api.libs.ws.WSProxyServer): Self = this

   def withQueryStringParameters(parameters: (String, String)*): Self = this

   def withRequestFilter(filter: play.api.libs.ws.WSRequestFilter): Self = this

   def withRequestTimeout(timeout: scala.concurrent.duration.Duration): Self = this

   def withUrl(url: String): Self = this

   def withVirtualHost(vh: String): Self = this
   
  }

}


final class FakeWSClient(
  requests: PartialFunction[String,(Int,String,Option[String])]
)
extends WSClient
{

  override def close(): Unit = ()

  override def underlying[T]: T = ???

  override def url(url: String): WSRequest = {

    val (status,statusText,body) = requests(url)

    FakeWSClient.Request(
      url,
      None,
      FakeWSClient.Response(
        URI.create(url),
        body,
        status,
        statusText
      )
    )
  }

}
