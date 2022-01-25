package net.girkin.gomoku3.store

import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import doobie.postgres.pgisimplicits.*
import io.circe.Json
import net.girkin.gomoku3.Ids.{GameEventId, GameId, JoinGameRequestId}
import net.girkin.gomoku3.store.GameEvent.GameCreated
import io.circe.syntax.*
import net.girkin.gomoku3.IdsCirceCodecs.given
import net.girkin.gomoku3.DoobieIdRepresentations.given

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

object PsqlGameEventQueries {
  def insertGameCreatedQuery(
    gameCreated: GameCreated
  ): ConnectionIO[GameEvent.GameCreated] =
    val jsonData: Json = Map(
      "leftJoinRequestId" -> gameCreated.leftJoinRequestId,
      "rightJoinRequestId" -> gameCreated.rightJoinRequestId
    ).asJson
    sql"""
         |insert into game_events (id, event, game_id, data, created_at)
         |values (${gameCreated.id}, 'GameCreated', ${gameCreated.gameId}, ${jsonData} ,${Instant.now()})
         |returning id
         |""".stripMargin
      .query[GameEventId]
      .unique
      .map(_ => gameCreated)
}
