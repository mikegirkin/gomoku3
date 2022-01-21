package net.girkin.gomoku3

import net.girkin.gomoku3.Ids.*
import doobie.implicits.*
import doobie.{Get, Put}
import doobie.implicits.javasql.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import doobie.postgres.pgisimplicits.*

import java.util.UUID

object PsqlDoobieIdRepresentations {

  implicit val userIdGet: Get[UserId] = Get[UUID].tmap { uuid => UserId.fromUUID(uuid) }
  implicit val userIdPut: Put[UserId] = Put[UUID].tcontramap { userId => userId.toUUID }

  implicit val gameIdGet: Get[GameId] = Get[UUID].tmap { uuid => GameId.fromUUID(uuid) }
  implicit val gameIdPut: Put[GameId] = Put[UUID].tcontramap { gameId => gameId.toUUID }

}
