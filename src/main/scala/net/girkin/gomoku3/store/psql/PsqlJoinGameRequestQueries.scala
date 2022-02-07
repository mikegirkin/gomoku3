package net.girkin.gomoku3.store.psql

import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import doobie.postgres.pgisimplicits.*
import net.girkin.gomoku3.DoobieIdRepresentations.given
import net.girkin.gomoku3.Ids.*
import net.girkin.gomoku3.store.{JoinGameRequestQueries, JoinRequestRecord}

object PsqlJoinGameRequestQueries extends JoinGameRequestQueries {

  def openedJoinRequestQuery(): ConnectionIO[Vector[JoinRequestRecord]] = {
    val query =
      sql"""
           |select join_requests.id, join_requests.user_id, join_requests.created_at
           |from join_requests
           |  left join game_events gc on
           |    gc.event = 'GameCreated' and
           |    (join_requests.id = uuid(gc.data->>'leftJoinRequestId') or join_requests.id = uuid(gc.data->>'rightJoinRequestId'))
           |where gc.id is null
           |""".stripMargin
    query.query[JoinRequestRecord]
      .to[Vector]
  }

  def insertJoinGameRequestQuery(joinRequestRecord: JoinRequestRecord): ConnectionIO[JoinRequestRecord] = {
    val query =
      sql"""
           |insert into join_requests (id, user_id, created_at)
           |values (${joinRequestRecord.id}, ${joinRequestRecord.userId}, ${joinRequestRecord.createdAt})
           |returning id
           |""".stripMargin
    query.query[JoinGameRequestId]
      .map(_ => joinRequestRecord)
      .unique
  }

  override def getActiveForUser(userId: UserId): ConnectionIO[Vector[JoinRequestRecord]] = {
    val query =
      sql"""
           |select join_requests.id, join_requests.user_id, join_requests.created_at
           |from join_requests
           |  left join game_events ge on
           |    ge.event = 'GameCreated' and
           |    (join_requests.id = uuid(ge.data->>'leftJoinRequestId') or join_requests.id = uuid(ge.data->>'rightJoinRequestId'))
           |where ge.id is null and join_requests.user_id = ${userId}
           |""".stripMargin
    query.query[JoinRequestRecord]
      .to[Vector]
  }
}
