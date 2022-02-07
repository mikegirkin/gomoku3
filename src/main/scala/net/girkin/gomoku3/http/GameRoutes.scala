package net.girkin.gomoku3.http

import cats.effect.IO
import net.girkin.gomoku3.{GameRules, Logging}
import net.girkin.gomoku3.auth.{AuthUser, CookieAuth}
import net.girkin.gomoku3.store.{GameStateStore, JoinGameService}
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import io.circe.*
import io.circe.syntax.*
import Codecs.given
import org.http4s.circe.CirceEntityEncoder._

class GameRoutes(
  auth: CookieAuth[IO],
  gameRoutesService: GameRoutesService
) extends Http4sDsl[IO] with Logging {

  private val ActiveQueryParam = new OptionalQueryParamDecoderMatcher[Boolean]("active") {}

  val routes = auth.secured(
    AuthedRoutes.of[AuthUser.AuthToken, IO] {
      case GET -> Root / "games" :? ActiveQueryParam(active) as token => gameRoutesService.listGames(token, active)
      case GET -> Root / "games" / UUIDVar(id) / "state" as token => ???
      case GET -> Root / "games" / UUIDVar(id) / "moves" as token => ???
      case PUT -> Root / "games" / UUIDVar(id) / "moves" as token => ???
      case POST -> Root / "games" / "joinRandom" as token => gameRoutesService.joinRandomGame(token).flatMap(_ => Accepted(""))
    }
  )

}

class GameRoutesService(
  gameStateStore: GameStateStore,
  joinGameService: JoinGameService,
  defaultGameRules: GameRules
) extends Http4sDsl[IO] with Logging {

  def listGames(token: AuthUser.AuthToken, active: Option[Boolean]): IO[Response[IO]] = {
    for {
      games <- gameStateStore.getForUser(token.userId, active)
      response <- Ok(games.asJson)
    } yield {
      response
    }
  }

  def joinRandomGame(token: AuthUser.AuthToken): IO[Unit] = {
    for {
      _ <- joinGameService.saveJoinGameRequest(token.userId)
      gamesCreated <- joinGameService.createGames(defaultGameRules)
    } yield {
      ()
    }
  }
}


