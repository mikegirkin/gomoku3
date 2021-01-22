package net.girkin.gomoku3.store

import cats.effect._
import net.girkin.gomoku3._

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

