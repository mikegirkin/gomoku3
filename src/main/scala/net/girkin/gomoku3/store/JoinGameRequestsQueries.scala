package net.girkin.gomoku3.store

import doobie.ConnectionIO
import net.girkin.gomoku3.Ids.*

import java.time.Instant

case class JoinRequestRecord(
  id: JoinGameRequestId,
  userId: UserId,
  createdAt: Instant
)

object JoinRequestRecord {
  def create(userId: UserId): JoinRequestRecord = JoinRequestRecord(JoinGameRequestId.create, userId, Instant.now())
}

trait JoinGameRequestQueries {
  def openedJoinRequestQuery(): ConnectionIO[Vector[JoinRequestRecord]]
  def insertJoinGameRequestQuery(joinRequestRecord: JoinRequestRecord): ConnectionIO[JoinRequestRecord]
  def getActiveForUser(userId: UserId): ConnectionIO[Vector[JoinRequestRecord]]
}
