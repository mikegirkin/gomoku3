package net.girkin.gomoku3.http

import net.girkin.gomoku3.store.{GameDBRecord, MoveDbRecord}
import io.circe.*
import io.circe.Codec.*
import io.circe.Decoder.decodeUUID
import io.circe.Encoder.encodeUUID
import io.circe.generic.semiauto.*
import cats.syntax.either.*
import net.girkin.gomoku3.Ids.{GameId, MoveId, UserId}
import net.girkin.gomoku3.http.GameRoutesService.{JoinGameError, MoveRequestFulfilled}
import net.girkin.gomoku3.{CellState, GameState, IdCreator, MoveAttemptFailure, MoveMade, OpaqueUUIDExtensions, Player}
import CellState.*

import java.util.UUID

object Codecs {

  private def idCodec[T <: UUID](converter: OpaqueUUIDExtensions[T] & IdCreator[T]): Codec[T] =
    Codec.from(
      decodeUUID.map(converter.fromUUID),
      encodeUUID.contramap(converter.idToUUID)
    )

  given gameIdCodec: Codec[GameId] = idCodec(GameId)
  given userIdCodec: Codec[UserId] = idCodec(UserId)
  given Codec[MoveId] = idCodec(MoveId)

  given gameDBRecordCodec: Codec[GameDBRecord] = deriveCodec[GameDBRecord]

  given playerCodec: Codec[Player] = deriveCodec[Player]
  given cellStateCoded: Codec[CellState] = Codec.from(
    Decoder.decodeOption[Player].map(x => CellState.fromOption(x)),
    Encoder.encodeOption[Player].contramap(x => x.asOption)
  )
  given Codec[MoveMade] = deriveCodec[MoveMade]
  given gameStateCodec: Codec[GameState] = deriveCodec[GameState]

  given joinGameErrorCodec: Codec[JoinGameError] = deriveCodec[JoinGameError]

  given Codec[RowCol] = deriveCodec[RowCol]

  given Codec[MoveAttemptFailure] = deriveCodec[MoveAttemptFailure]
  given Codec[MoveRequestFulfilled] = deriveCodec[MoveRequestFulfilled]
  given Codec[MoveDbRecord] = deriveCodec[MoveDbRecord]
}
