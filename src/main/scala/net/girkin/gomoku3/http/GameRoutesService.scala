package net.girkin.gomoku3.http

import cats.data.EitherT
import cats.effect.IO
import net.girkin.gomoku3.auth.AuthUser
import net.girkin.gomoku3.store.{GameDBRecord, GameStateStore, JoinGameService}
import net.girkin.gomoku3.{GameRules, GameState, Logging}
import org.http4s.Response
import org.http4s.dsl.Http4sDsl
import org.http4s.circe.CirceEntityEncoder.*
import net.girkin.gomoku3.Ops.*
import cats.syntax.either.*
import Codecs.given
import io.circe.*
import io.circe.syntax.*

class GameRoutesService(
  gameStateStore: GameStateStore,
  joinGameService: JoinGameService,
  defaultGameRules: GameRules
) extends Http4sDsl[IO] with Logging {

  import GameRoutesService.*

  def listGames(token: AuthUser.AuthToken, active: Option[Boolean]): IO[Vector[GameDBRecord]] = {
    gameStateStore.getForUser(token.userId, active)
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