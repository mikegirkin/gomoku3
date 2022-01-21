package net.girkin.gomoku3.store

import cats.effect.IO
import doobie.util.transactor.Transactor
import net.girkin.gomoku3.{GameRules, GameState}
import net.girkin.gomoku3.Ids.*
import net.girkin.gomoku3.auth.{PsqlUserRepository, User}
import net.girkin.gomoku3.testutil.IOTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

class PsqlGameStateStoreSpec extends AnyWordSpec with Matchers with IOTest {
  val xa = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql://localhost:5432/gomoku3"
  )

  val userStore = new PsqlUserRepository(xa)
  val gameStateStore = new PsqlGameStateStore(xa)

  "PsqlGameStateStore" should {
    "be able to save game state" in {
      val userOneId = UserId.create
      val userOne = User(userOneId, userOneId.toString + "@test.com", Instant.now())
      val userTwoId = UserId.create
      val userTwo = User(userTwoId, userTwoId.toString + "@test.com", Instant.now())
      val gameState = GameState.create(userOne.userId, userTwo.userId, GameRules(3, 3, 3))

      val result = for {
        _ <- userStore.insertOrUpdate(userOne)
        _ <- userStore.insertOrUpdate(userTwo)
        _ <- gameStateStore.insert(gameState)
      } yield {
        ()
      }

      result.unsafeRunSync() shouldBe ()
    }
  }
}
