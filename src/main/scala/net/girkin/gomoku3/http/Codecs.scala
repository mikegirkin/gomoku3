package net.girkin.gomoku3.http

import net.girkin.gomoku3.store.GameDBRecord
import io.circe.*
import io.circe.Codec.*
import io.circe.Decoder.decodeUUID
import io.circe.Encoder.encodeUUID
import io.circe.generic.semiauto.*
import cats.syntax.either.*
import net.girkin.gomoku3.Ids.{GameId, UserId}
import net.girkin.gomoku3.{IdCreator, OpaqueUUIDExtensions}

import java.util.UUID

object Codecs {

  private def idCodec[T <: UUID](converter: OpaqueUUIDExtensions[T] & IdCreator[T]): Codec[T] =
    Codec.from(
      decodeUUID.map(converter.fromUUID),
      encodeUUID.contramap(converter.idToUUID)
    )

  given gameIdCodec: Codec[GameId] = idCodec(GameId)
  given userIdCodec: Codec[UserId] = idCodec(UserId)

  given gameDBRecordCodec: Codec[GameDBRecord] = deriveCodec[GameDBRecord]
}
