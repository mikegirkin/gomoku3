package net.girkin.gomoku3.store

import cats.effect._
import net.girkin.gomoku3.{GameRules, MoveRequest}

import java.time.Instant
import java.util.UUID

trait Id[T >: UUID] {
  def create: T = UUID.randomUUID()
}

opaque type GameId = UUID
object GameId extends Id[GameId]

opaque type MoveId = UUID
object MoveId extends Id[MoveId]

opaque type PlayerId = UUID
object PlayerId extends Id[PlayerId]

case class MoveMade(
  id: MoveId,
  row: Int,
  col: Int,
  player: PlayerId
)

case class GameState(
  gameId: GameId,
  rules: GameRules,
  created: Instant,
  playerOne: PlayerId,
  playerTwo: PlayerId,
  movesMade: Vector[MoveMade]
)

trait GameStateStore {
  def save(request: GameState): IO[Unit]
  def getLatest(gameId: GameId): IO[Option[GameState]]
}

