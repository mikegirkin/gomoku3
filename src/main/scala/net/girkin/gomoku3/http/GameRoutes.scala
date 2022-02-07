package net.girkin.gomoku3.http

import cats.effect.IO
import net.girkin.gomoku3.{GameRules, Logging}
import net.girkin.gomoku3.auth.{AuthUser, CookieAuth}
import net.girkin.gomoku3.store.{GameDBRecord, GameStateStore, JoinGameService}
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import io.circe.*
import io.circe.syntax.*
import Codecs.given
import cats.data.EitherT
import org.http4s.circe.CirceEntityEncoder.*
import net.girkin.gomoku3.Ops.*
import cats.syntax.either.*

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

  import GameRoutesService.*

  def listGames(token: AuthUser.AuthToken, active: Option[Boolean]): IO[Response[IO]] = {
    for {
      games <- gameStateStore.getForUser(token.userId, active)
      response <- Ok(games.asJson)
    } yield {
      response
    }
  }

  def joinRandomGame(token: AuthUser.AuthToken): IO[Either[JoinGameError, Unit]] = {
    val resultF = for {
      activeGames <- EitherT.right(
        gameStateStore.getForUser(token.userId, activeFilter = Some(true))
      )
      _ <- EitherT.cond[IO](
        activeGames.isEmpty,
        (),
        JoinGameError.UserAlreadyInActiveGame,
      )
      existingRequests <- joinGameService.getActiveUserRequests(token.userId) |> EitherT.right.apply
      _ <- EitherT.cond(
        existingRequests.isEmpty,
        (),
        JoinGameError.UserAlreadyQueued
      )
      _ <- joinGameService.saveJoinGameRequest(token.userId) |> EitherT.right.apply
      gamesCreated <- joinGameService.createGames(defaultGameRules) |> EitherT.right.apply
    } yield {
      ()
    }
    resultF.value
  }
}

object GameRoutesService {
  enum JoinGameError:
    case UserAlreadyInActiveGame
    case UserAlreadyQueued
}


