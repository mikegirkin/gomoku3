package net.girkin.gomoku3

import java.util.UUID

trait Id[T >: UUID] {
  def create: T = UUID.randomUUID()
}

opaque type GameId = UUID
object GameId extends Id[GameId]

opaque type MoveId = UUID
object MoveId extends Id[MoveId]

opaque type PlayerId = UUID
object PlayerId extends Id[PlayerId]