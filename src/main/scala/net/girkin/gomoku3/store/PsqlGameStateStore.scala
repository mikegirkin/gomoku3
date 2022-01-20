package net.girkin.gomoku3.store

import cats.effect.IO
import net.girkin.gomoku3.GameState
import net.girkin.gomoku3._

class PsqlGameStateStore() extends GameStateStore {
  override def save(request: GameState): IO[Unit] = ???

  override def getLatest(gameId: GameId): IO[Option[GameState]] = ???
}
