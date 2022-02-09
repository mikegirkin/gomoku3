package net.girkin.gomoku3.http

import cats.effect.IO
import net.girkin.gomoku3.{GameRules, GameState, Logging}
import net.girkin.gomoku3.auth.{AuthUser, CookieAuth}
import net.girkin.gomoku3.store.{GameDBRecord, GameStateStore, JoinGameService}
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import io.circe.*
import io.circe.syntax.*
import org.http4s.circe.*
import Codecs.given
import net.girkin.gomoku3.http.GameRoutesService.JoinGameError
import net.girkin.gomoku3.Ids.GameId
import GameRoutesService.*

class GameRoutes(
  auth: CookieAuth[IO],
  gameRoutesService: GameRoutesService
) extends Http4sDsl[IO] with Logging {

  private val ActiveQueryParam = new OptionalQueryParamDecoderMatcher[Boolean]("active") {}

  val routes = auth.secured(
    AuthedRoutes.of[AuthUser.AuthToken, IO] {
      case GET -> Root / "games" :? ActiveQueryParam(active) as token =>
        gameRoutesService.listGames(token, active).flatMap { games =>
          Ok(games.asJson)
        }
      case GET -> Root / "games" / UUIDVar(id) as token =>
        gameRoutesService.getGameState(token, GameId.fromUUID(id)).flatMap {
          case AccessError.NotFound => NotFound()
          case AccessError.AccessDenied => Forbidden()
          case gameState: GameState => Ok(gameState.asJson)
        }
      case GET -> Root / "games" / UUIDVar(id) / "moves" as token => ???
      case PUT -> Root / "games" / UUIDVar(id) / "moves" as token => ???
      case POST -> Root / "games" / "joinRandom" as token =>
        gameRoutesService.joinRandomGame(token).flatMap {
          case Left(reason) =>
            val content: Json = Json.obj("reason" -> reason.asJson)
            BadRequest(content)
          case Right(_) => Accepted()
        }
    }
  )

}
