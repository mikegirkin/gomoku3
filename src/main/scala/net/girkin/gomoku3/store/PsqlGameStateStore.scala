package net.girkin.gomoku3.store

import cats.effect.IO
import net.girkin.gomoku3.GameState
import net.girkin.gomoku3.*
import net.girkin.gomoku3.Ids.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import doobie.postgres.pgisimplicits.*

class PsqlGameStateStore(transactor: Transactor[IO]) extends GameStateStore {
  import PsqlDoobieIdRepresentations.*

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
    
    ???
  }

}
