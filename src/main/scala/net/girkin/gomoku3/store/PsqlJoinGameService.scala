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
import net.girkin.gomoku3.store.PsqlJoinGameRequestsQueries._

import java.time.Instant

class PsqlJoinGameService(
  gameStateStore: GameStateStore,
  transactor: Transactor[IO]
) {
  import net.girkin.gomoku3.DoobieIdRepresentations.given

  def saveJoinGameRequest(userId: UserId): IO[JoinRequestRecord] = {
    val record = JoinRequestRecord.create(userId)
    insertJoinGameRequestQuery(record)
      .transact(transactor)
      .map(_ => record)
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

