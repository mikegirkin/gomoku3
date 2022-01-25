package net.girkin.gomoku3

import net.girkin.gomoku3.Ids.UserId

import java.util.UUID

trait IdCreator[T] {
  def create: T = fromUUID(UUID.randomUUID())
  def fromUUID(uuid: UUID): T
}

trait OpaqueUUIDExtensions[T <: UUID] {
  def idToUUID(x: T): UUID = x

  extension (x: T) {
    def toUUID: UUID = idToUUID(x)
  }
}

object Ids {


  opaque type GameId <: UUID = UUID
  object GameId extends IdCreator[GameId] with OpaqueUUIDExtensions[GameId] {
    override def fromUUID(uuid: UUID): GameId = uuid
  }

  opaque type MoveId <: UUID = UUID
  object MoveId extends IdCreator[MoveId] with OpaqueUUIDExtensions[MoveId] {
    override def fromUUID(uuid: UUID): MoveId = uuid
  }

  opaque type UserId <: UUID = UUID
  object UserId extends IdCreator[UserId] with OpaqueUUIDExtensions[UserId] {
    override def fromUUID(uuid: UUID): UserId = uuid
  }

  opaque type JoinGameRequestId <: UUID = UUID
  object JoinGameRequestId extends IdCreator[JoinGameRequestId] with OpaqueUUIDExtensions[JoinGameRequestId] {
    override def fromUUID(uuid: UUID): JoinGameRequestId = uuid
  }

  opaque type GameCreatedId <: UUID = UUID
  object GameCreatedId extends IdCreator[GameCreatedId] with OpaqueUUIDExtensions[GameCreatedId] {
    override def fromUUID(uuid: UUID): GameCreatedId = uuid
  }

}

