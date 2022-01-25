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
  import net.girkin.gomoku3.PsqlDoobieIdRepresentations.given

  private case class JoinRequestRecord(
    id: JoinGameRequestId,
    userId: UserId,
    createdAt: Instant
  )

  private case class GameCreatedRecord(
    id: GameCreatedId,
    requestLeft: JoinGameRequestId,
    requestRight: JoinGameRequestId,
    gameId: GameId,
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

  def createGames(gameRules: GameRules): IO[Vector[GameState]] = {
    val query = sql"""
      |select join_requests.id, join_requests.user_id, join_requests.created_at
      |from join_requests
      |  left join game_created gc on join_requests.id = gc.request_left or join_requests.id = gc.request_right
      |where gc.id is null
      |""".stripMargin

    val waitingRequestsF: ConnectionIO[Vector[JoinRequestRecord]] = query
      .query[JoinRequestRecord]
      .to[Vector]

    val pairedWaitingRequestsF: ConnectionIO[Vector[(JoinRequestRecord, JoinRequestRecord)]] =
      waitingRequestsF.map { joinRequestsVector =>
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

    def insertGameCreatedQuery(
      requestLeftId: JoinGameRequestId,
      requestRightId: JoinGameRequestId,
      gameId: GameId
    ): ConnectionIO[Option[GameCreatedRecord]] =
      sql"""
        |insert into game_created (id, request_left, request_right, game_id, created_at)
        |values (${GameCreatedId.create}, ${requestLeftId}, ${requestRightId}, ${gameId}, ${Instant.now()})
        |returning id, request_left, request_right, game_id, created_at
        |""".stripMargin
        .query[GameCreatedRecord]
        .option

    val newGame = GameState.create(leftRequest.userId, rightRequest.userId, gameRules)
    for {
      _ <- gameStateStore.insertQuery(newGame)
      gameCreatedRecord <- insertGameCreatedQuery(leftRequest.id, rightRequest.id, newGame.gameId)
    } yield {
      newGame
    }
  }
}
