package net.girkin.gomoku3.store.psql

import net.girkin.gomoku3.{GameRules, GameState}
import net.girkin.gomoku3.Ids.{GameId, UserId}
import net.girkin.gomoku3.auth.{PsqlUserRepository, User, UserReporitory}
import net.girkin.gomoku3.store.{GameDBRecord, GameEvent, JoinRequestRecord}
import net.girkin.gomoku3.testutil.{DBTest, IOTest}
import org.scalatest.{BeforeAndAfterAll, Inside}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cats.effect.*
import doobie.*
import doobie.implicits.*
import cats._
import cats.implicits._

import java.time.Instant

class PsqlGameStateQueriesSpec extends AnyWordSpec with Matchers with IOTest with DBTest with Inside {

  def createTestUser(): User = {
    val userId = UserId.create
    User(userId, s"${userId}@test.com", Instant.now())
  }

  "PsqlGameStateQueries" should {
    val user = Range.inclusive(0, 1).map { _ =>
      createTestUser()
    }.toVector
    val joinRequests = List(
      JoinRequestRecord.create(user(0).id),
      JoinRequestRecord.create(user(1).id),
      JoinRequestRecord.create(user(0).id),
      JoinRequestRecord.create(user(1).id),
    )
    val game = Vector(
      GameState.create(user(0).id, user(1).id, GameRules(3, 3, 3)),
      GameState.create(user(0).id, user(1).id, GameRules(3, 3, 3))
    )

    val events = List(
      GameEvent.gameCreated(game(0).gameId, joinRequests(0).id, joinRequests(1).id),
      GameEvent.gameCreated(game(1).gameId, joinRequests(2).id, joinRequests(3).id),
      GameEvent.gameFinished(game(1).gameId),
    )

    val insertGameThings: ConnectionIO[Unit] = for {
      _ <- joinRequests.map(PsqlJoinGameRequestQueries.insertJoinGameRequestQuery).sequence
      _ <- game.map(PsqlGameStateQueries.insertQuery).sequence
      _ <- events.map(PsqlGameEventQueries.insertGameEventQuery).sequence
    } yield {
      ()
    }

    val userRepository = new PsqlUserRepository(tr)
    val prereqs = for {
      _ <- user.map(userRepository.insertOrUpdate).sequence
      _ <- insertGameThings.transact(tr)
    } yield {
      ()
    }

    prereqs.unsafeRunSync()

    "be able to return active games" in ioTest {
      for {
        result <- PsqlGameStateQueries.getForUserQuery(user(0).id, Some(true)).transact(tr)
      } yield {
        inside(result) {
          case Vector(rec @ GameDBRecord(gameId, createdAt, playerOne, playerTwo, height, width, winCondition)) =>
            rec shouldBe GameDBRecord.fromGameState(game(0))
        }
      }
    }

    "be able to return inactive games" in ioTest {
      for {
        result <- PsqlGameStateQueries.getForUserQuery(user(0).id, Some(false)).transact(tr)
      } yield {
        inside(result) {
          case Vector(rec : GameDBRecord) =>
            rec shouldBe GameDBRecord.fromGameState(game(1))
        }
      }
    }

    "be ablt to return inactive games" in ioTest {
      for {
        result <- PsqlGameStateQueries.getForUserQuery(user(0).id, None).transact(tr)
      } yield {
        val expected = game.map(GameDBRecord.fromGameState)
        result should contain theSameElementsAs expected
      }
    }
  }
}
