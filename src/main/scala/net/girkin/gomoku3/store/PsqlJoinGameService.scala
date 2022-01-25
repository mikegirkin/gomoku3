package net.girkin.gomoku3.store

import cats.effect.IO
import cats.*
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import doobie.postgres.pgisimplicits.*
import net.girkin.gomoku3.{GameRules, GameState}
import net.girkin.gomoku3.Ids.*
import net.girkin.gomoku3.Ops.|>

import java.time.Instant

class PsqlJoinGameService(
  gameStateStore: GameStateStore,
  transactor: Transactor[IO]
) {
  import net.girkin.gomoku3.DoobieIdRepresentations.given

  private case class JoinRequestRecord(
    id: JoinGameRequestId,
    userId: UserId,
    createdAt: Instant
  )

  def saveJoinGameRequest(userId: UserId): IO[Unit] = {
    val query = sql"""
      |insert into join_requests (id, user_id, created_at)
      |values (gen_random_uuid(), ${userId}, now() at time zone 'Z')
      |""".stripMargin
    query.update
      .run
      .transact(transactor)
      .map(_ => ())
  }

  private def openedJoinRequestQuery(): ConnectionIO[Vector[JoinRequestRecord]] = {
    val query = sql"""
      |select join_requests.id, join_requests.user_id, join_requests.created_at
      |from join_requests
      |  left join game_events gc on
      |    gc.event = 'GameCreated' and
      |    (join_requests.id = uuid(gc.data->>'leftJoinRequestId') or join_requests.id = uuid(gc.data->>'rightJoinRequestId'))
      |where gc.id is null
      |""".stripMargin
    query.query[JoinRequestRecord]
      .to[Vector]
  }

  def createGames(gameRules: GameRules): IO[Vector[GameState]] = {
    val pairedWaitingRequestsF: ConnectionIO[Vector[(JoinRequestRecord, JoinRequestRecord)]] =
      openedJoinRequestQuery().map { joinRequestsVector =>
        joinRequestsVector.grouped(2).collect {
          case Vector(left, right) => (left, right)
        }.toVector
      }

    val transaction: ConnectionIO[Vector[GameState]] = pairedWaitingRequestsF.flatMap { pairsVector =>
      pairsVector.map {
        case (leftJoinRequest, rightJoinRequest) => createAndSaveGame(gameRules, leftJoinRequest, rightJoinRequest)
      }.sequence
    }

    transaction.transact(transactor)
  }

  private def createAndSaveGame(
    gameRules: GameRules,
    leftRequest: JoinRequestRecord,
    rightRequest: JoinRequestRecord
  ): ConnectionIO[GameState] = {
    val newGame = GameState.create(leftRequest.userId, rightRequest.userId, gameRules)
    for {
      _ <- gameStateStore.insertQuery(newGame)
      event = GameEvent.gameCreated(newGame.gameId, leftRequest.id, rightRequest.id)
      gameCreatedRecord <- PsqlGameEventQueries.insertGameCreatedQuery(event)
    } yield {
      newGame
    }
  }
}

