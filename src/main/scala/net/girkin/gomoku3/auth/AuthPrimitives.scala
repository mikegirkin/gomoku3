package net.girkin.gomoku3.auth

import cats.*
import cats.implicits.*
import org.http4s.{Response, ResponseCookie}

import java.util.Base64
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.util.Random
import net.girkin.gomoku3.Ids.*



case class AuthToken(
  userId: UserId
)

class AuthPrimitives[F[_]: Monad](key: PrivateKey) {

  val authCookieName = "auth"
  val crypto = CryptoBits(key)

  def signToken(token: AuthToken): F[String] = {
    val serializedToken = Base64.getEncoder.encodeToString(token.userId.toString().getBytes("utf-8"))
    val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
    implicitly[Applicative[F]].pure(
      Range(1, 16).map { _ => Random.nextInt(chars.size) }.mkString("")
    ).map {
      nonce => crypto.signToken(serializedToken, nonce)
    }
  }

  def setAuthCookie(response: F[Response[F]], token: AuthToken): F[Response[F]] = {
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

}
