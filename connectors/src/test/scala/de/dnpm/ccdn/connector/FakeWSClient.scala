package de.dnpm.ccdn.connector


import java.net.URI
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import org.apache.pekko.util.ByteString
import play.api.libs.ws.{
  StandaloneWSClient => WSClient,
  StandaloneWSRequest => WSRequest,
  StandaloneWSResponse => WSResponse,
  WSAuthScheme,
  WSBody,
  BodyWritable,
  WSCookie,
  WSProxyServer,
  WSRequestFilter,
  WSSignatureCalculator
}


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

    def bodyAsBytes = ByteString.fromString(body)

    def bodyAsSource = ???

    def cookie(name: String): Option[WSCookie] = None

    def cookies: Seq[WSCookie] = Seq.empty

    def headers: Map[String,Seq[String]] = Map.empty

    def underlying[T]: T = ???

  }


  final case class Request(
    url: String,
    optBody: Option[WSBody],
    response: Response,
    headers: Map[String,Seq[String]] = Map.empty,
    cookies: Seq[WSCookie] = Seq.empty
  )
  extends WSRequest
  {

   type Self = FakeWSClient.Request
   type Response = FakeWSClient.Response


   def auth: Option[(String, String, WSAuthScheme)] = None

   def body = optBody.get

   def calc: Option[WSSignatureCalculator] = None

   def contentType: Option[String] = None

   def delete(): Future[Response] = Future.successful(response)

   def execute(): Future[Response] = Future.successful(response)

   def execute(method: String): Future[Response] = Future.successful(response)

   def followRedirects: Option[Boolean] = None

   def get(): Future[Response] = Future.successful(response)

   def head(): Future[Response] = Future.successful(response)

   def method: String = ""

   def options(): Future[Response] = Future.successful(response)
   
   def patch[T](body: T)(implicit bw: BodyWritable[T]): Future[Response] = Future.successful(response)

   def post[T](body: T)(implicit bw: BodyWritable[T]): Future[Response] = Future.successful(response)

   def proxyServer: Option[WSProxyServer] = None

   def put[T](body: T)(implicit bw: BodyWritable[T]): Future[Response] = Future.successful(response)

   def queryString: Map[String,Seq[String]] = Map.empty

   def requestTimeout: Option[Duration] = None

   def sign(calc: WSSignatureCalculator): Self = this

   def stream(): Future[Response] = Future.successful(response)

   
   def uri = URI.create(url)

   def virtualHost: Option[String] = None

   def withAuth(username: String, password: String, scheme: WSAuthScheme): Self = this
   
   def withBody[T](body: T)(implicit bw: BodyWritable[T]): Self = this 
   
   def withCookies(cookies: WSCookie*): Self = copy(cookies = this.cookies ++ cookies)

   def withDisableUrlEncoding(disableUrlEncoding: Boolean): Self = this

   def withFollowRedirects(follow: Boolean): Self = this

   def withHttpHeaders(headers: (String, String)*): Self =
     copy(
       headers = headers.map { case (name,value) => name -> Seq(value) }.toMap
     )

   def withMethod(method: String): Self = this

   def withProxyServer(proxyServer: WSProxyServer): Self = this

   def withQueryStringParameters(parameters: (String, String)*): Self = this

   def withRequestFilter(filter: WSRequestFilter): Self = this

   def withRequestTimeout(timeout: Duration): Self = this

   def withUrl(url: String): Self = copy(url = url)

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
