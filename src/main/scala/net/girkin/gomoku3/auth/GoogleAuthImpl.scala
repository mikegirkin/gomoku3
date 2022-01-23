package net.girkin.gomoku3.auth

import java.time.{Instant, LocalDateTime}
import java.util.{Base64, UUID}
import cats.data.EitherT
import cats.effect.{Concurrent, Resource, Sync}
import cats.implicits.*
import cats.{Applicative, Monad}
import io.circe.Decoder.Result
import io.circe.{Decoder, HCursor, Json}
import net.girkin.gomoku3.*
import org.http4s.client.Client
import org.http4s.implicits.*
import org.http4s.headers.{Cookie, Location}
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import Ops.*
import org.http4s.dsl.Http4sDsl

import scala.concurrent.duration.*
import scala.util.Random

case class SecurityConfiguration(
  googleClientId: String,
  googleSecret: String
)

enum GoogleAuthError:
  case GoogleCallbackError(msg: String) extends GoogleAuthError
  case GoogleAuthResponseIncomprehensible extends GoogleAuthError

class GoogleAuthImpl[F[_]: Concurrent](
  authPrimitives: CookieAuth[F],
  userStore: UserReporitory[F],
  config: SecurityConfiguration,
  httpClient: Resource[F, Client[F]]
) extends Http4sDsl[F] with Logging {
  import GoogleAuthError._

  val REDIRECT_AFTER_LOGIN_TO = s"http://${ServerConfiguration.host}:${ServerConfiguration.port}/auth/google/callback"

  val service = HttpRoutes.of[F] {
    case GET -> Root / "begin" => startAuthProcess(REDIRECT_AFTER_LOGIN_TO)
    case request @ GET -> Root / "google" / "callback" => processCallback(request)
  }

  def startAuthProcess(redirectUri: String): F[Response[F]] = {
    val state = UUID.randomUUID().toString
    val nonce = UUID.randomUUID().toString

    val redirectUri = uri"https://accounts.google.com/o/oauth2/v2/auth".withQueryParams(
      Map[String, String](
        "client_id" -> config.googleClientId,
        "response_type" -> "code",
        "scope" -> "openid email",
        "redirect_uri" -> s"$REDIRECT_AFTER_LOGIN_TO",
        "state" -> state,
        "nonce" -> nonce
      )
    )
    SeeOther(
      Location(redirectUri)
    ).map {
      _.addCookie(ResponseCookie(
        "google-auth-state",
        state,
        maxAge = Some(5 * 60),
        path = Some("/")
      ))
    }
  }

  private def requestGooogleUserData(code: String)  = {
    case class GoogleUserResponse(tokenType: String, idToken: String)

    given decoder: Decoder[GoogleUserResponse] = new Decoder[GoogleUserResponse] {
      override def apply(c: HCursor): Result[GoogleUserResponse] = {
        for {
          tokenType <- c.downField("token_type").as[String]
          idToken <- c.downField("id_token").as[String]
        } yield {
          GoogleUserResponse(tokenType, idToken)
        }
      }
    }

    val requestBody = Seq(
      "code" -> code,
      "client_id" -> config.googleClientId,
      "client_secret" -> config.googleSecret,
      "redirect_uri" -> REDIRECT_AFTER_LOGIN_TO,
      "grant_type" -> "authorization_code"
    )

    val request = Request[F](
      Method.POST,
      uri"https://www.googleapis.com/oauth2/v4/token",
      headers = Headers.of(org.http4s.headers.`Content-Type`(MediaType.application.`x-www-form-urlencoded`))
    ).withEntity(
      UrlForm(requestBody: _*)
    )(UrlForm.entityEncoder)

    for {
      googleUserResponse <- httpClient.use {
        client => client.expectOr[GoogleUserResponse](request) {
          error =>
            logger.info(error.toString())

            error.body.through(fs2.text.utf8.decode).compile
              .fold(Vector.empty[String])({
                case (acc, str) => acc :+ str
              }).map {
                v => logger.info(v.mkString("\n"))
              }.map {
                _ => new Exception("failed on requesting stuff from google")
              }
        }
      }
    } yield {
      /* TODO: This require extracting keys from google discovery document to do the correct validation
     ref http://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata
    */
      io.circe.parser.parse(
        new String(
          Base64.getDecoder.decode(
            googleUserResponse.idToken.split('.')(1)
          )
        )
      ).getOrElse(Json.Null)
    }
  }

  private def getOrCreateUser(email: String): F[User] = {
    for {
      storedUser <- userStore.getByEmail(email)
      resultingUser <- {
        storedUser.fold {
          val newUser = User.create(email)
          userStore.insertOrUpdate(newUser).map{ _ => newUser }
        } {
          u => implicitly[Monad[F]].pure(u)
        }
      }
    } yield resultingUser
  }

  def processCallback(request: Request[F]): F[Response[F]] = {
    val code: Either[GoogleAuthError, String] = for {
      req_state <- request.params.get("state").toRight(GoogleCallbackError("Parameter state not found"))
      cookie <- request.headers.get[Cookie].toRight(GoogleCallbackError("Required cookie not found"))
      cookie_state <- cookie.values.find(_.name == "google-auth-state").toRight(GoogleCallbackError("Required cookie not found"))
      result <- if (cookie_state.content == req_state) {
        request.params.get("code").toRight(GoogleCallbackError("Required cookie not found"))
      } else {
        Either.left(GoogleCallbackError("Required cookie not found"))
      }
    } yield result

    val response: EitherT[F, GoogleAuthError, F[Response[F]]] = for {
      code <- EitherT.fromEither[F](code)
      userData <- EitherT.right[GoogleAuthError](requestGooogleUserData(code))
      email <- {
        EitherT.fromEither[F](userData.hcursor.get[String]("email").leftMap[GoogleAuthError](
          _ => GoogleAuthResponseIncomprehensible))
      }
      user <- EitherT.right[GoogleAuthError](getOrCreateUser(email))
    } yield {
      logger.info(s"Logging in user ${user}")
      authPrimitives.setAuthCookie(
        SeeOther(
          Location(Uri.fromString("/").toOption.get)
        ).map(_.removeCookie(
          ResponseCookie(
            "google-auth-state",
            "",
            path = Some("/")
          )
        )),
        AuthUser.AuthToken(user.userId)
      )
    }

    response.fold(
    {
      case x @ GoogleCallbackError(_) =>
        logger.error(x.toString)
        BadRequest()
      case GoogleAuthResponseIncomprehensible => InternalServerError()
    },
    identity
    ).flatten
  }
}


