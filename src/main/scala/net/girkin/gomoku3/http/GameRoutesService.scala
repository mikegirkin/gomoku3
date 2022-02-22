package net.girkin.gomoku3.http

import cats.data.EitherT
import cats.*
import cats.effect.IO
import net.girkin.gomoku3.auth.AuthUser
import net.girkin.gomoku3.store.{GameDBRecord, GameStateStore, JoinGameService, MoveDbRecord}
import net.girkin.gomoku3.{Game, GameMoveRequest, GameRules, GameState, Logging, MoveAttemptFailure, Player}
import org.http4s.Response
import org.http4s.dsl.Http4sDsl
import org.http4s.circe.CirceEntityEncoder.*
import net.girkin.gomoku3.Ops.*
import net.girkin.gomoku3.EitherTOps.*
import cats.syntax.either.*
import Codecs.given
import io.circe.*
import io.circe.syntax.*
import net.girkin.gomoku3.Ids.{GameId, MoveId, UserId}

class GameRoutesService(
  gameStateStore: GameStateStore,
  joinGameService: JoinGameService,
  defaultGameRules: GameRules
) extends Http4sDsl[IO] with Logging {

  import GameRoutesService.*

  private def requireOrFail[F[_]: Applicative, T](condition: Boolean, ifNotFulfilled: => T): EitherT[F, T, Unit] = {
    EitherT.cond[F](
      condition,
      (),
      ifNotFulfilled
    )
  }

  def listGames(token: AuthUser.AuthToken, active: Option[Boolean]): IO[Vector[GameDBRecord]] = {
    gameStateStore.getForUser(token.userId, active)
  }

  def joinRandomGame(token: AuthUser.AuthToken): IO[Either[JoinGameError, Unit]] = {
    val resultF = for {
      activeGames <- EitherT.right(
        gameStateStore.getForUser(token.userId, activeFilter = Some(true))
      )
      _ <- requireOrFail(activeGames.isEmpty, JoinGameError.UserAlreadyInActiveGame)
      existingRequests <- joinGameService.getActiveUserRequests(token.userId) |> EitherT.right.apply
      _ <- requireOrFail(existingRequests.isEmpty, JoinGameError.UserAlreadyQueued)
      _ <- joinGameService.saveJoinGameRequest(token.userId) |> EitherT.right.apply
      gamesCreated <- joinGameService.createGames(defaultGameRules) |> EitherT.right.apply
    } yield {
      ()
    }
    resultF.value
  }

  def getGameState(token: AuthUser.AuthToken, gameId: GameId): IO[AccessError | GameState] = {
    val resultF: EitherT[IO, AccessError, GameState] = for {
      gameOpt <- EitherT.right { gameStateStore.get(gameId) }
      game <- EitherT.fromOption[IO](gameOpt, AccessError.NotFound)
      _ <- requireOrFail(
        game.playerOne == token.userId || game.playerTwo == token.userId,
        AccessError.AccessDenied
      )
    } yield {
      game
    }

    resultF.merge
  }

  private def getGameForUser(gameId: GameId, userId: UserId): EitherT[IO, AccessError, (GameState, Player)] = {
    for {
      gameOpt <- EitherT.right {
        gameStateStore.get(gameId)
      }
      gameState <- EitherT.fromOption[IO](gameOpt, AccessError.NotFound)
      playerOpt = gameState.whichPlayer(userId)
      player <- EitherT.fromOption[IO](playerOpt, AccessError.AccessDenied)
    } yield {
      gameState -> player
    }
  }

  def postMove(moveRequest: MoveRequest): IO[PostMoveResult] = {
    val resultF = for {
      gameAndPlayer <- getGameForUser(moveRequest.gameId, moveRequest.userId)
        .widenLeft[PostMoveResult]
      (gameState, player) = gameAndPlayer
      newGame: Game <- EitherT.fromEither[IO] {
        gameState.game.attemptMove(GameMoveRequest(moveRequest.row, moveRequest.col, player))
      }.widenLeft[PostMoveResult]
    } yield  {
      val lastMove = newGame.movesMade.last
      MoveRequestFulfilled(lastMove.moveId, moveRequest)
    }

    resultF.merge
  }

  def getMoves(userId: UserId, gameId: GameId): IO[AccessError | Vector[MoveDbRecord]] = {
    val resultF = for {
      gameAndPlayer <- getGameForUser(gameId, userId)
      moves <- EitherT.right { gameStateStore.getMoves(gameId) }
    } yield {
      moves
    }

    resultF.merge
  }
}

object GameRoutesService {
  type PostMoveResult = AccessError | MoveAttemptFailure | MoveRequestFulfilled

  enum JoinGameError {
    case UserAlreadyInActiveGame
    case UserAlreadyQueued
  }

  enum AccessError {
    case AccessDenied
    case NotFound
  }

  case class MoveRequest(
    gameId: GameId,
    userId: UserId,
    row: Int,
    col: Int
  )

  case class MoveRequestFulfilled(
    moveId: MoveId,
    request: MoveRequest
  )

}