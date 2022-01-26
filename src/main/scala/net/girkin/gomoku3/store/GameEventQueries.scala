package net.girkin.gomoku3.store

import doobie.*
import net.girkin.gomoku3.Ids.*
import net.girkin.gomoku3.store.GameEvent

import java.time.Instant

enum GameEvent(id: GameEventId, gameId: GameId, createdAt: Instant):
  case GameCreated(
    id: GameEventId,
    gameId: GameId,
    leftJoinRequestId: JoinGameRequestId,
    rightJoinRequestId: JoinGameRequestId,
    createdAt: Instant,
  ) extends GameEvent(id, gameId, createdAt)

  case GameFinished(id: GameEventId, gameId: GameId, createdAt: Instant) extends GameEvent(id, gameId, createdAt)

object GameEvent {
  def gameCreated(gameId: GameId, leftJoinRequestId: JoinGameRequestId, rightJoinRequestId: JoinGameRequestId): GameCreated = {
    GameEvent.GameCreated(GameEventId.create, gameId, leftJoinRequestId, rightJoinRequestId, Instant.now())
  }
}

trait GameEventQueries {
  def insertGameCreatedQuery(gameCreated: GameEvent.GameCreated): ConnectionIO[GameEvent.GameCreated]
}
