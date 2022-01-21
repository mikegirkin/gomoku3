package net.girkin.gomoku3

import net.girkin.gomoku3.Ids.UserId

import java.util.UUID

trait IdCreator[T >: UUID] {
  def create: T = UUID.randomUUID()
  def fromUUID(uuid: UUID): T = uuid
}

trait OpaqueUUIDExtensions[T <: UUID] {
  extension (x: T) {
    def toUUID: UUID = x
  }
}

object Ids {

  opaque type GameId = UUID
  object GameId extends IdCreator[GameId] with OpaqueUUIDExtensions[GameId]

  opaque type MoveId = UUID
  object MoveId extends IdCreator[MoveId] with OpaqueUUIDExtensions[MoveId]

  opaque type UserId = UUID
  object UserId extends IdCreator[UserId] with OpaqueUUIDExtensions[UserId]

}

