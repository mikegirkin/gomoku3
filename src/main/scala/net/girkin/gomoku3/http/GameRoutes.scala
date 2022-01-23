package net.girkin.gomoku3.http

import cats.effect.IO
import net.girkin.gomoku3.Logging
import net.girkin.gomoku3.auth.{AuthUser, CookieAuth}
import net.girkin.gomoku3.store.GameStateStore
import org.http4s.{AuthedRoutes, HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl

class GameRoutes(
  auth: CookieAuth[IO],
  gameRoutesService: GameRoutesService
) extends Http4sDsl[IO] with Logging {

  val routes = auth.secured(
    AuthedRoutes.of[AuthUser.AuthToken, IO] {
      case GET -> Root / "games" as token => gameRoutesService.listGames(token)
      case GET -> Root / "games" / UUIDVar(id) / "state" as token => ???
      case GET -> Root / "games" / UUIDVar(id) / "moves" as token => ???
      case PUT -> Root / "games" / UUIDVar(id) / "moves" as token => ???
      case POST -> Root / "game" / "joinRandom" as token => ???
    }
  )

}

class GameRoutesService(
  gameStateStore: GameStateStore
) extends Logging {
  def listGames(token: AuthUser.AuthToken): IO[Response[IO]] = {
    ???
  }
}
