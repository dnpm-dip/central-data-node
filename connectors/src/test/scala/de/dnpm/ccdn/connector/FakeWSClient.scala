package de.dnpm.ccdn.connector


import java.net.URI
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import org.apache.pekko.util.ByteString
import play.api.libs.ws.{
  StandaloneWSClient => WSClient,
  StandaloneWSRequest => WSRequest,
  StandaloneWSResponse => WSResponse,
  EmptyBody,
  WSAuthScheme,
  BodyWritable,
  WSCookie,
  WSProxyServer,
  WSRequestFilter,
  WSSignatureCalculator
}

object HTTP
{

  val DELETE  = "DELETE"
  val GET     = "GET"
  val HEAD    = "HEAD"
  val OPTIONS = "OPTIONS"
  val POST    = "POST"
  val PATCH   = "PATCH"
  val PUT     = "PUT"

  val Ok                  = 200
  val Created             = 201
  val BadRequest          = 400
  val Unauthorized        = 401
  val Forbidden           = 403
  val InternalServerError = 500
  val BadGateway          = 502
  //TODO

  val statusTexts: Function[Int,String] =
    Map(
      Ok                  -> "OK",
      Created             -> "Created",
      BadRequest          -> "Bad Request",
      Unauthorized        -> "Unauthorized",
      Forbidden           -> "Forbidden",
      InternalServerError -> "Internal Server Error",
      BadGateway          -> "Bad Gateway"
      //TODO
    )
}


object FakeWSClient
{

  private final case class Response
  (
    uri: URI,
    optBody: Option[String],
    status: Int,
    statusText: String
  )
  extends WSResponse
  {

    def body = optBody.get

    def bodyAsBytes = ByteString.fromString(body)

    def bodyAsSource = ???

    def cookie(name: String): Option[WSCookie] = None

    def cookies: Seq[WSCookie] = Seq.empty

    def headers: Map[String,Seq[String]] = Map.empty

    def underlying[T]: T = ???

  }


  private final case class Request
  (
    url: String,
    responses: PartialFunction[String,Response],
  )
  extends WSRequest
  {

    import HTTP._

    type Self = FakeWSClient.Request
    type Response = FakeWSClient.Response
    
    
    def auth: Option[(String,String,WSAuthScheme)] = None
  
    // Fake request is not supposed to tranfer an actual body,
    // but is just a means to obtain a response
    def body = EmptyBody
    
    def calc: Option[WSSignatureCalculator] = None
    
    def contentType: Option[String] = None
    
    def cookies = Seq.empty
    
    def delete(): Future[Response] = execute(DELETE)
    
    def execute(): Future[Response] = ???
    
    def execute(method: String): Future[Response] = Future.successful(responses(method))
    
    def followRedirects: Option[Boolean] = None
    
    def get(): Future[Response] = execute(GET)
    
    def head(): Future[Response] = execute(HEAD)
    
    def headers = Map.empty
    
    def method: String = ""
    
    def options(): Future[Response] = execute(OPTIONS)
    
    def patch[T](body: T)(implicit bw: BodyWritable[T]): Future[Response] = execute(PATCH)
    
    def post[T](body: T)(implicit bw: BodyWritable[T]): Future[Response] = execute(POST) 
    
    def proxyServer: Option[WSProxyServer] = None
    
    def put[T](body: T)(implicit bw: BodyWritable[T]): Future[Response] = execute(PUT)
    
    def queryString: Map[String,Seq[String]] = Map.empty
    
    def requestTimeout: Option[Duration] = None
    
    def sign(calc: WSSignatureCalculator): Self = this
    
    def stream(): Future[Response] = ???
    
    def uri = URI.create(url)
    
    def virtualHost: Option[String] = None
    
    def withAuth(username: String, password: String, scheme: WSAuthScheme): Self = this
    
    def withBody[T](body: T)(implicit bw: BodyWritable[T]): Self = this 
    
    def withCookies(cookies: WSCookie*): Self = this
    
    def withDisableUrlEncoding(disableUrlEncoding: Boolean): Self = this
    
    def withFollowRedirects(follow: Boolean): Self = this
    
    def withHttpHeaders(headers: (String, String)*): Self = this
    
    def withMethod(method: String): Self = this
    
    def withProxyServer(proxyServer: WSProxyServer): Self = this
    
    def withQueryStringParameters(parameters: (String, String)*): Self = this
    
    def withRequestFilter(filter: WSRequestFilter): Self = this
    
    def withRequestTimeout(timeout: Duration): Self = this
    
    def withUrl(url: String): Self = copy(url = url)
    
    def withVirtualHost(vh: String): Self = this
  
  }

  // Map a URL to a Method to a Response: URL => Method => Response 
  type RequestMapper = PartialFunction[String,PartialFunction[String,(Int,Option[String])]]
  
  def apply(responses: RequestMapper): FakeWSClient =
    new FakeWSClient(responses)

}


final class FakeWSClient private (
  responses: FakeWSClient.RequestMapper
)
extends WSClient
{

  override def close(): Unit = ()

  override def underlying[T]: T = ???

  override def url(url: String): WSRequest =
    FakeWSClient.Request(
      url,
      responses(url).andThen {
       case (status,body) => 
         FakeWSClient.Response(
           URI.create(url),
           body,
           status,
           HTTP.statusTexts(status)
         )
      }
    )
  
}
