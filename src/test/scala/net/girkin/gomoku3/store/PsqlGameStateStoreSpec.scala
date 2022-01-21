package net.girkin.gomoku3.store

import cats.effect.IO
import doobie.util.transactor.Transactor
import net.girkin.gomoku3.{GameMoveRequest, GameRules, GameState}
import net.girkin.gomoku3.Ids.*
import net.girkin.gomoku3.auth.{PsqlUserRepository, User}
import net.girkin.gomoku3.testutil.IOTest
import net.girkin.gomoku3.Player
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
    val userOneId = UserId.create
    val userOne = User(userOneId, userOneId.toString + "@test.com", Instant.now())
    val userTwoId = UserId.create
    val userTwo = User(userTwoId, userTwoId.toString + "@test.com", Instant.now())
    val gameState = GameState.create(userOne.userId, userTwo.userId, GameRules(3, 3, 3))

    "be able to save initial game state" in {
      val result = for {
        _ <- userStore.insertOrUpdate(userOne)
        _ <- userStore.insertOrUpdate(userTwo)
        _ <- gameStateStore.insert(gameState)
        fetchedGame <- gameStateStore.get(gameState.gameId)
      } yield {
        fetchedGame
      }

      result.unsafeRunSync() shouldBe Some(gameState)
    }

    "be able to save moves and retrieve correct state back" in {
      val move = GameMoveRequest(1, 1, Player.One)
      val moveResult = gameState.game.attemptMove(move)

      moveResult.isRight shouldBe true

      val newGameState = gameState.copy(
        game = moveResult.toOption.get
      )
      val moveMade = newGameState.game.movesMade.last

      val fetchedGameF = for {
        _ <- gameStateStore.insertMove(gameState.gameId, moveMade)
        fetchedGame <- gameStateStore.get(gameState.gameId)
      } yield {
        fetchedGame
      }

      fetchedGameF.unsafeRunSync() shouldBe Some(newGameState)
    }
  }
}
