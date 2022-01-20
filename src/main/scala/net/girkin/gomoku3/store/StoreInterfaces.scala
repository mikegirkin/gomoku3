package net.girkin.gomoku3.store

import cats.effect.*
import net.girkin.gomoku3.*
import net.girkin.gomoku3.Ids.*

import java.time.Instant
import java.util.UUID

case class MoveMade(
  id: MoveId,
  row: Int,
  col: Int,
  player: PlayerId
)



trait GameStateStore {
  def save(request: GameState): IO[Unit]
  def getLatest(gameId: GameId): IO[Option[GameState]]
}

