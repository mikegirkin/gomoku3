package net.girkin.gomoku3.http

import cats.effect.IO
import net.girkin.gomoku3.{GameRules, GameState, Logging, MoveAttemptFailure}
import net.girkin.gomoku3.auth.{AuthUser, CookieAuth}
import net.girkin.gomoku3.store.{GameDBRecord, GameStateStore, JoinGameService}
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits.*
import io.circe.*
import io.circe.syntax.*
import io.circe.generic.semiauto.*
import Codecs.given
import net.girkin.gomoku3.http.GameRoutesService.JoinGameError
import net.girkin.gomoku3.Ids.GameId
import GameRoutesService.*

case class RowCol(
  row: Int,
  col: Int
)

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
      case req @ POST -> Root / "games" / UUIDVar(id) / "moves" as token =>
        for {
          rowCol <- req.req.as[RowCol]
          moveRequest = MoveRequest(GameId.fromUUID(id), token.userId, rowCol.row, rowCol.col)
          result <- gameRoutesService.postMove(moveRequest)
          response <- { result match
            case AccessError.NotFound => NotFound()
            case AccessError.AccessDenied => Forbidden()
            case x : MoveAttemptFailure => UnprocessableEntity.apply(x.asJson)
            case mrf : MoveRequestFulfilled => Created(mrf.asJson)
          }
        } yield {
          response
        }
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
