package net.girkin.gomoku3.store

import doobie.ConnectionIO
import net.girkin.gomoku3.Ids.{GameId, MoveId, UserId}
import net.girkin.gomoku3.{GameState, Player}

import java.time.Instant


case class GameDBRecord(
  gameId: GameId,
  createdAt: Instant,
  playerOne: UserId,
  playerTwo: UserId,
  height: Int,
  width: Int,
  winCondition: Int
)

object GameDBRecord {
  def fromGameState(gameState: GameState) = {
    GameDBRecord(gameState.gameId, gameState.createdAt, gameState.playerOne, gameState.playerTwo,
      gameState.game.rules.height, gameState.game.rules.width, gameState.game.rules.winCondition)
  }
}

case class MoveDbRecord(
  moveId: MoveId,
  gameId: GameId,
  seq: Int,
  createdAt: Instant,
  row: Int,
  col: Int,
  playerNumber: Player
)

object MoveDbRecord {
  def create(gameId: GameId, seq: Int, row: Int, col: Int, playerNumber: Player): MoveDbRecord = {
    MoveDbRecord(MoveId.create, gameId, seq, Instant.now(), row, col, playerNumber)
  }
}

trait GameStateQueries {
  def insertQuery(game: GameState): ConnectionIO[Unit]

  def getByIdQuery(gameId: GameId): ConnectionIO[Option[GameDBRecord]]

  def getForUserQuery(userId: UserId, activeFilter: Option[Boolean]): ConnectionIO[Vector[GameDBRecord]]

  def insertMoveQuery(gameId: GameId, moveId: MoveId, row: Int, col: Int, playerNumber: Player): ConnectionIO[MoveDbRecord]

  def getMovesQuery(gameId: GameId): ConnectionIO[Vector[MoveDbRecord]]
}
