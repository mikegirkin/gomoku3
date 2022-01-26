package net.girkin.gomoku3.store

import cats.data.OptionT
import cats.effect.IO
import cats.syntax.either.*
import doobie.*
import doobie.implicits.*
import net.girkin.gomoku3.GameState
import net.girkin.gomoku3.*
import net.girkin.gomoku3.Ids.*

import java.time.Instant

class GameStateStore(
  gameStateQueries: GameStateQueries,
  transactor: Transactor[IO]
) {

  def insert(game: GameState): IO[Unit] = {
    gameStateQueries.insertQuery(game).transact(transactor)
  }

  def get(gameId: GameId): IO[Option[GameState]] = {
    val query = for {
      gameRecord <- OptionT { gameStateQueries.getByIdQuery(gameId) }
      moves <- OptionT.liftF { gameStateQueries.getMovesQuery(gameId) }
    } yield {
      restoreGameStateFromRecords(gameRecord, moves)
    }

    query.value.transact(transactor)
  }

  def insertMove(gameId: GameId, moveMade: MoveMade): IO[MoveDbRecord] = {
    gameStateQueries.insertMoveQuery(gameId, moveMade.moveId, moveMade.row, moveMade.col, moveMade.player)
      .transact(transactor)
  }

  private def restoreGameStateFromRecords(gameRecord: GameDBRecord, moveRecords: Seq[MoveDbRecord]): GameState = {
    val game = Game.create(GameRules(gameRecord.height, gameRecord.width, gameRecord.winCondition))

    val gameWithMoves = moveRecords.foldLeft(Either.right[MoveAttemptFailure, Game](game)) {
      case (gameEither, move) => gameEither.flatMap {
        _.executeMoveMade(MoveMade(move.moveId, move.row, move.col, move.playerNumber))
      }
    }.getOrElse(game)

    GameState(
      gameRecord.gameId,
      gameRecord.createdAt,
      gameRecord.playerOne,
      gameRecord.playerTwo,
      gameWithMoves
    )
  }

}
