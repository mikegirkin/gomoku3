package net.girkin.gomoku3.testutil

import cats.Monad
import cats.implicits.*
import cats.effect.*
import cats.effect.unsafe.IORuntime
import doobie.util.transactor.Transactor
import net.girkin.gomoku3.Ids.UserId
import net.girkin.gomoku3.auth.AuthUser.AuthToken
import net.girkin.gomoku3.auth.CookieAuth
import org.http4s.Request

trait IOTest {

  given runtime: IORuntime = cats.effect.unsafe.implicits.global

  def ioTest[T](block: IO[T]): Unit =
    block.unsafeRunSync()

}

trait DBTest {
  val tr = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql://localhost:5432/gomoku3"
  )
}

trait HttpAuth {
  def setRequestAuthCookie[F[_]: Monad](auth: CookieAuth[F])(req: Request[F], userId: UserId): F[Request[F]] = {
    val token: AuthToken = AuthToken(userId)
    for {
      token <- auth.signToken(token)
    } yield {
      req.addCookie(auth.authCookieName, token)
    }
  }
}
