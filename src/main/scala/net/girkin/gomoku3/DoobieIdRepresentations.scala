package net.girkin.gomoku3

import net.girkin.gomoku3.Ids.*
import doobie.implicits.*
import doobie.{Get, Meta, Put}
import doobie.implicits.javasql.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import doobie.postgres.pgisimplicits.*
import io.circe.Json
import io.circe.parser._
import cats.syntax.either._
import org.postgresql.util.PGobject

import java.util.UUID

object DoobieIdRepresentations {

  private def idMeta[T <: UUID](converter: OpaqueUUIDExtensions[T] & IdCreator[T]): Meta[T] =
    Meta[UUID].timap(converter.fromUUID)(converter.idToUUID)

  given userIdGet: Meta[UserId] = idMeta[UserId](UserId)
  given gameIdGet: Meta[GameId] = idMeta[GameId](GameId)
  given moveIdMeta: Meta[MoveId] = idMeta[MoveId](MoveId)
  given joinGameRequestIdMeta: Meta[JoinGameRequestId] = idMeta[JoinGameRequestId](JoinGameRequestId)
  given gameEventMeta: Meta[GameEventId] = idMeta[GameEventId](GameEventId)

  given playerMeta: Meta[Player] = Meta[Int].timap {
    case 1 => Player.One
    case 2 => Player.Two
  } {
    _.value
  }

  given jsonMeta: Meta[Json] =
    Meta.Advanced.other[PGobject]("json").timap[Json](
      a => parse(a.getValue).leftMap[Json](e => throw e).merge)(
      a => {
        val o = new PGobject
        o.setType("json")
        o.setValue(a.noSpaces)
        o
      }
    )
}


