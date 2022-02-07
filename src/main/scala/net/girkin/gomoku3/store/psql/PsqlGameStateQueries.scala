package net.girkin.gomoku3.store.psql

import cats.data.NonEmptyList
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import doobie.postgres.pgisimplicits.*
import net.girkin.gomoku3.DoobieIdRepresentations.given
import net.girkin.gomoku3.Ids.{GameId, MoveId, UserId}
import net.girkin.gomoku3.store.{GameDBRecord, GameStateQueries, MoveDbRecord}
import net.girkin.gomoku3.{GameState, Player}

import java.time.Instant

object PsqlGameStateQueries extends GameStateQueries {

  def insertQuery(game: GameState): ConnectionIO[Unit] = {
    val query =
      sql"""insert into games (id, created_at, player_one, player_two, height, width, win_condition)
           |values (${game.gameId}, ${game.createdAt}, ${game.playerOne}, ${game.playerTwo},
           |  ${game.game.rules.height}, ${game.game.rules.width}, ${game.game.rules.winCondition})
           |""".stripMargin
    query.update
      .run
      .map(_ => ())
  }

  def getByIdQuery(gameId: GameId): ConnectionIO[Option[GameDBRecord]] = {
    sql"""
         |select id, created_at, player_one, player_two, height, width, win_condition
         |from games
         |where id = ${gameId}
    """.stripMargin
      .query[GameDBRecord]
      .option
  }


  override def getForUserQuery(
    userId: UserId, activeFilter: Option[Boolean]
  ): ConnectionIO[Vector[GameDBRecord]] = {
    val expectedValuesForGameFinishedEvent = activeFilter.map(x => NonEmptyList.one(x)).getOrElse(NonEmptyList(true, List(false)))
    val q =
      fr"""select g.id, g.created_at, player_one, player_two, height, width, win_condition
          |from games g
          |  left join game_events ge on g.id = ge.game_id and event = 'GameFinished'
          |where
          |  (player_one = ${userId} or player_two = ${userId}) and """.stripMargin
        ++ Fragments.in(
             fr"""case when ge.id is null then TRUE
                 |     else FALSE
                 |end""".stripMargin, expectedValuesForGameFinishedEvent)
    q.query[GameDBRecord]
      .to[Vector]
  }

  def insertMoveQuery(gameId: GameId, moveId: MoveId, row: Int, col: Int, playerNumber: Player): ConnectionIO[MoveDbRecord] = {
    val createdAt = Instant.now()
    sql"""
         |insert into game_moves (id, game_id, seq, created_at, row, col, player_number)
         |values (${moveId}, ${gameId},
         |  (select coalesce(max(seq)+1, 0) from game_moves where game_id = ${gameId}),
         |  ${createdAt}, ${row}, ${col}, ${playerNumber})
         |returning id, game_id, seq, created_at, row, col, player_number
    """.stripMargin
      .query[MoveDbRecord]
      .unique
  }

  def getMovesQuery(gameId: GameId): ConnectionIO[Vector[MoveDbRecord]] = {
    val query =
      sql"""
           |select id, game_id, seq, created_at, row, col, player_number from game_moves
           |where game_id = ${gameId}
           |order by seq
           |""".stripMargin
    query.query[MoveDbRecord]
      .to[Vector]
  }
}
