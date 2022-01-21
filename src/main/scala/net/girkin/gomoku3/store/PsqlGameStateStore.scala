package net.girkin.gomoku3.store

import cats.data.OptionT
import cats.effect.IO
import cats.syntax.either.*
import net.girkin.gomoku3.GameState
import net.girkin.gomoku3.*
import net.girkin.gomoku3.Ids.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import doobie.postgres.pgisimplicits.*

import java.time.Instant

class PsqlGameStateStore(transactor: Transactor[IO]) extends GameStateStore {
  import PsqlDoobieIdRepresentations.*

  private case class GameRecord(
    gameId: GameId,
    createdAt: Instant,
    playerOne: UserId,
    playerTwo: UserId,
    height: Int,
    width: Int,
    winCondition: Int
  )

  private case class MoveRecord(
    moveId: MoveId,
    gameId: GameId,
    seq: Int,
    createdAt: Instant,
    row: Int,
    col: Int,
    playerNumber: Player
  )

  override def insert(game: GameState): IO[Unit] = {
    val query = sql"""insert into games (id, created_at, player_one, player_two, height, width, win_condition)
         |values (${game.gameId}, ${game.createdAt}, ${game.playerOne}, ${game.playerTwo},
         |  ${game.game.rules.height}, ${game.game.rules.width}, ${game.game.rules.winCondition})
         |""".stripMargin
    query.update
      .run
      .transact(transactor)
      .map(_ => ())
  }

  override def get(gameId: GameId): IO[Option[GameState]] = {
    val selectGameQuery = sql"""
      |select id, created_at, player_one, player_two, height, width, win_condition
      |from games
      |where id = ${gameId}""".stripMargin
      .query[GameRecord]
      .option

    val query = for {
      gameRecord <- OptionT { selectGameQuery }
      moves <- OptionT.liftF { getMovesQuery(gameId) }
    } yield {
      restoreGameStateFromRecords(gameRecord, moves)
    }

    query.value.transact(transactor)
  }

  def insertMove(gameId: GameId, moveMade: MoveMade): IO[Unit] = {
    val query = sql"""
      |insert into game_moves (id, game_id, seq, created_at, row, col, player_number)
      |values (${moveMade.moveId}, ${gameId},
      |  (select coalesce(max(seq)+1, 0) from game_moves where game_id = ${gameId}),
      |  ${Instant.now()}, ${moveMade.row}, ${moveMade.col}, ${moveMade.player})
      |""".stripMargin
    query.update
      .run
      .map(_ => ())
      .transact(transactor)
  }

  private def getMovesQuery(gameId: GameId): ConnectionIO[Vector[MoveRecord]] = {
    val query = sql"""
      |select id, game_id, seq, created_at, row, col, player_number from game_moves
      |where game_id = ${gameId}
      |order by seq
      |""".stripMargin
    query.query[MoveRecord]
      .to[Vector]
  }

  private def restoreGameStateFromRecords(gameRecord: GameRecord, moveRecords: Seq[MoveRecord]): GameState = {
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
