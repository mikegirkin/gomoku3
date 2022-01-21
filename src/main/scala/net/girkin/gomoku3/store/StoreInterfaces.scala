package net.girkin.gomoku3.store

import cats.effect.*
import net.girkin.gomoku3.*
import net.girkin.gomoku3.Ids.*

import java.time.Instant
import java.util.UUID

trait GameStateStore {
  def insert(request: GameState): IO[Unit]
  def get(gameId: GameId): IO[Option[GameState]]
}

