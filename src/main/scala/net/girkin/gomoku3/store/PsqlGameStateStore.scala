package net.girkin.gomoku3.store

import cats.effect.IO
import net.girkin.gomoku3.GameState
import net.girkin.gomoku3.*
import net.girkin.gomoku3.Ids.*


class PsqlGameStateStore() extends GameStateStore {
  override def save(request: GameState): IO[Unit] = {
    val gameId = GameId.create
    gameId.toUUID
    ???
  }

  override def getLatest(gameId: GameId): IO[Option[GameState]] = ???
}
