package net.girkin.gomoku3.http

import cats.data.EitherT
import cats.*
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
import net.girkin.gomoku3.Ids.GameId

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
}

object GameRoutesService {
  enum JoinGameError:
    case UserAlreadyInActiveGame
    case UserAlreadyQueued

  enum AccessError:
    case AccessDenied
    case NotFound

}