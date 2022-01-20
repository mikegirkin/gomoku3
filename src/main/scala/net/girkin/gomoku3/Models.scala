package net.girkin.gomoku3

import java.util.UUID

trait IdCreator[T >: UUID] {
  def create: T = UUID.randomUUID()
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

  opaque type PlayerId = UUID
  object PlayerId extends IdCreator[PlayerId] with OpaqueUUIDExtensions[PlayerId]
}
