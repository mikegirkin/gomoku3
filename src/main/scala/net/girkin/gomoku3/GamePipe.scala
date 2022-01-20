package net.girkin.gomoku3

import fs2._

import java.time._
import cats.data.EitherT
import cats.implicits._
import cats.effect._
import net.girkin.gomoku3._
import net.girkin.gomoku3.store.GameStateStore

enum GameFinishReason {
  case PlayerWon(playerId: PlayerId)
  case PlayerConceded(playerId: PlayerId)
}

enum GameStateUpdate(gameId: GameId) {
  case WrongRequest(gameId: GameId) extends GameStateUpdate(gameId)   
  case MoveMade(gameId: GameId, playerId: PlayerId, row: Int, col: Int) extends GameStateUpdate(gameId)
  case GameFinished(gameId: GameId) extends GameStateUpdate(gameId)
}

case class MoveRequest(
  gameId: GameId,
  playerId: PlayerId,
  row: Int,
  column: Int
)

class GamePipe private (
  gameStateRef: Ref[IO, GameState],
  gameStore: GameStateStore
) {
  
  def pipe: Pipe[IO, MoveRequest, List[GameStateUpdate]] = {
    (requestStream: Stream[IO, MoveRequest]) => requestStream.evalMap(handleMoverequest)
  }
  
  private def executeMoveRequest(gameState: GameState, request: MoveRequest): Either[GameStateUpdate, GameState] = {
    for {
      _ <- Either.cond(gameState.gameId == request.gameId, (), GameStateUpdate.WrongRequest(request.gameId))
      playerNumber <- gameState.whichPlayer(request.playerId).toRight(GameStateUpdate.WrongRequest(request.gameId))
      newGame <- gameState.game.attemptMove(GameMoveRequest(request.row, request.column, playerNumber)).leftMap(_ => GameStateUpdate.WrongRequest(request.gameId))
    } yield {
      gameState.copy(
        game = newGame
      )
    }
  }
  
  private def updateGameStateStores(gameState: GameState): IO[GameState] = {
    for {
      _ <- gameStore.save(gameState)
      result <- gameStateRef.modify(_ => (gameState, gameState))
    } yield {
      result
    }
  }

  private def handleMoverequest(request: MoveRequest): IO[List[GameStateUpdate]] = {
    val requestExecutionResult = for {
      gameState <- EitherT.right[GameStateUpdate](gameStateRef.get)
      newGameState <- EitherT(IO.pure(executeMoveRequest(gameState, request)))
      savedState <- EitherT.right(updateGameStateStores(newGameState))
    } yield {
      val moveMadeUpdate: GameStateUpdate = GameStateUpdate.MoveMade(request.gameId, request.playerId, request.row, request.column) 
      val gameEndedUpdate: Option[GameStateUpdate] = newGameState.game.winResult.map { playerWon => GameStateUpdate.GameFinished(request.gameId) }
      List(moveMadeUpdate) ++ gameEndedUpdate.toList
    }
    
    requestExecutionResult.leftMap(err => List(err)).merge
  }
}

object GamePipe {
  def create(gameStateStore: GameStateStore)(gameId: GameId, playerOne: PlayerId, playerTwo: PlayerId, game: Game): IO[GamePipe] = {
    val state = GameState(gameId, created = Instant.now, playerOne, playerTwo, game)
    for {
      gameStateRef <- Ref.of[IO, GameState](state)
    } yield {
      new GamePipe(gameStateRef, gameStateStore)
    }
  }
  
}

class GamePipes private (store: Ref[IO, Map[GameId, GamePipe]]) {
  
  def getGamePipe(gameId: GameId): IO[Option[GamePipe]] = {
    for {
      knownPipes <- store.get
    } yield {
      knownPipes.get(gameId)
    }
  }
  
  def removeGamePipe(gameId: GameId): IO[Unit] = {
    store.modify {
      knownPipes => (knownPipes.removed(gameId), ())
    }
  }
  
  def registerGamePipe(gameId: GameId, pipe: GamePipe): IO[GamePipe] = {
    store.modify {
      knownPipes => (knownPipes.updated(gameId, pipe), pipe)
    }
  }

}

object GamePipes {
  
  def create(): IO[GamePipes] = {
    for {
      ref <- Ref.of[IO, Map[GameId, GamePipe]](Map.empty)
    } yield {
      new GamePipes(ref)
    }
  }
}