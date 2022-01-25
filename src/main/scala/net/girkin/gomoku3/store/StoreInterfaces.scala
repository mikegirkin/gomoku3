package net.girkin.gomoku3.store

import cats.effect.*
import net.girkin.gomoku3.*
import net.girkin.gomoku3.Ids.*
import doobie.free.ConnectionIO

import java.time.Instant
import java.util.UUID

enum GameStatus:
  case Active
  case Finished

case class ShortGameRecord(
  gameId: GameId,
  createdAt: Instant,
  playerOne: UserId,
  playerTwo: UserId,
  status: GameStatus,
  rules: GameRules
)

trait GameStateStore {
  def insertQuery(game: GameState): ConnectionIO[Unit]
  def insert(request: GameState): IO[Unit]
  def get(gameId: GameId): IO[Option[GameState]]
  def listForUser(userId: UserId): IO[List[ShortGameRecord]]
}

