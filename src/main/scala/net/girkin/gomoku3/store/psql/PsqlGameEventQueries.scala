package net.girkin.gomoku3.store.psql

import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import doobie.postgres.pgisimplicits.*
import io.circe.Json
import io.circe.syntax.*
import net.girkin.gomoku3.DoobieIdRepresentations.given
import net.girkin.gomoku3.Ids.{GameEventId, GameId, JoinGameRequestId}
import net.girkin.gomoku3.IdsCirceCodecs.given
import net.girkin.gomoku3.store.{GameEvent, GameEventQueries}
import net.girkin.gomoku3.store.GameEvent.{GameCreated, GameFinished}

import java.time.Instant

object PsqlGameEventQueries extends GameEventQueries {
  def insertGameCreatedQuery(
    gameCreated: GameCreated
  ): ConnectionIO[GameEvent.GameCreated] = {
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

  def insertGameFinishedQuery(
    gameFinished: GameFinished
  ): ConnectionIO[GameEvent.GameFinished] = {
    val jsonData = Json.obj()
    sql"""
         |insert into game_events (id, event, game_id, data, created_at)
         |values (${gameFinished.id}, 'GameFinished', ${gameFinished.gameId}, ${jsonData} ,${Instant.now()})
         |returning id
         |""".stripMargin
      .query[GameEventId]
      .unique
      .map(_ => gameFinished)
  }

  def insertGameEventQuery(
    gameEvent: GameEvent
  ): ConnectionIO[GameEvent] = {
    gameEvent match {
      case gc : GameEvent.GameCreated =>
        insertGameCreatedQuery(gc).map[GameEvent](identity)
      case gf : GameEvent.GameFinished =>
        insertGameFinishedQuery(gf).map[GameEvent](identity)
    }
  }
}
