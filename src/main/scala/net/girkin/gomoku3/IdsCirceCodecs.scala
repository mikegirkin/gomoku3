package net.girkin.gomoku3

import io.circe.*
import io.circe.syntax.*
import cats.syntax.either.*
import net.girkin.gomoku3.Ids.JoinGameRequestId

import java.util.UUID

object IdsCirceCodecs {

  given joinGameRequestIdCodec: Codec[JoinGameRequestId] =
    Codec.from[JoinGameRequestId](
      Decoder.decodeUUID.emap(JoinGameRequestId.fromUUID andThen Either.right),
      Encoder.encodeUUID.contramap(JoinGameRequestId.toUUID)
    )
}
