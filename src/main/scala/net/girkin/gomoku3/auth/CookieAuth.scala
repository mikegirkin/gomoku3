package net.girkin.gomoku3.auth

import cats.*
import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import cats.implicits.*
import io.circe.Decoder.Result
import io.circe.{Codec, Decoder, Encoder, HCursor}
import org.http4s.{AuthedRoutes, Request, Response, ResponseCookie, Status, Uri, headers}

import java.util.{Base64, UUID}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.util.Random
import net.girkin.gomoku3.Ids.*
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.Responses
import org.http4s.headers.{Cookie, Location}
import org.http4s.server.AuthMiddleware
import org.http4s.util.CaseInsensitiveString
import org.typelevel.ci.CIString

enum AuthUser:
  case AuthToken(userId: UserId) extends AuthUser
  case Anonymous extends AuthUser

enum AuthErr:
  case NoAuthCookie
  case CookieInvalid

object AuthCodecs {
  given authErrCodec: Codec[AuthErr] = Codec.from(
    Decoder[String].emap { str =>
      if(str == AuthErr.NoAuthCookie.toString) Either.right(AuthErr.NoAuthCookie)
      else if (str == AuthErr.CookieInvalid.toString) Either.right(AuthErr.CookieInvalid)
      else Either.left("Could not decode AuthErr")
    },
    Encoder[String].contramap { authErr =>
      authErr.toString
    }
  )
}

class CookieAuth[F[_]: Monad](
  key: PrivateKey,
  loginPageUri: Uri
) extends Http4sDsl[F]{

  val authCookieName = "auth"
  val crypto = CryptoBits(key)

  def signToken(token: AuthUser.AuthToken): F[String] = {
    val serializedToken = Base64.getEncoder.encodeToString(token.userId.toString().getBytes("utf-8"))
    val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
    implicitly[Applicative[F]].pure(
      Range(1, 16).map { _ => Random.nextInt(chars.size) }.mkString("")
    ).map {
      nonce => crypto.signToken(serializedToken, nonce)
    }
  }

  def setAuthCookie(response: F[Response[F]], token: AuthUser.AuthToken): F[Response[F]] = {
    for {
      resp <- response
      signedToken <- signToken(token)
    } yield {
      resp.addCookie(
        ResponseCookie(
          authCookieName,
          signedToken,
          path = Some("/"),
          maxAge = Some(Duration(1, TimeUnit.DAYS).toMillis)
        )
      )
    }
  }

  def removeAuthCookie(response: Response[F]): Response[F] = {
    response.removeCookie(
      ResponseCookie(
        authCookieName,
        "",
        path = Some("/"),
        maxAge = Some(Duration(1, TimeUnit.DAYS).toMillis)
      )
    )
  }

  private def authenticate: Kleisli[F, Request[F], Either[AuthErr, AuthUser.AuthToken]] = Kleisli[F, Request[F], Either[AuthErr, AuthUser.AuthToken]]({ request =>
    implicitly[Applicative[F]].pure {
      for {
        header <- request.headers.get[Cookie].toRight(AuthErr.NoAuthCookie)
        cookie <- header.values.find(_.name == authCookieName).toRight(AuthErr.NoAuthCookie)
        tokenStr <- crypto.validateSignedToken(cookie.content).toRight(AuthErr.CookieInvalid)
        userUUID = UUID.fromString(
          new String(
            Base64.getDecoder.decode(tokenStr), "utf-8"
          )
        )
        token: AuthUser.AuthToken = AuthUser.AuthToken(UserId.fromUUID(userUUID))
      } yield {
        token
      }
    }
  })

  private def allowAnonymous(authToken: Either[AuthErr, AuthUser]): Either[AuthErr, AuthUser] = authToken match {
    case Left(AuthErr.NoAuthCookie) => Right(AuthUser.Anonymous)
    case x => x
  }

  private val onAuthFailure = AuthedRoutes[AuthErr, F] { req =>
    import net.girkin.gomoku3.auth.AuthCodecs.authErrCodec
    import org.http4s.circe.CirceEntityCodec._

    OptionT.liftF(
      if(req.req.headers
        .get(CIString("X-Requested-With"))
        .exists { _.exists(_.value == "XMLHttpRequest") }
      ) {
        val content = req.context
        Forbidden(content)
      } else {
        SeeOther(Location(loginPageUri))
      }
    )
  }


  val secured = AuthMiddleware(authenticate, onAuthFailure.map(removeAuthCookie))

  val authenticated = AuthMiddleware(authenticate.map(allowAnonymous), onAuthFailure.map(removeAuthCookie))
}
