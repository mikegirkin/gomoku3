package net.girkin.gomoku3.store

import cats.effect.IO
import doobie.util.transactor.Transactor
import net.girkin.gomoku3.{GameMoveRequest, GameRules, GameState}
import net.girkin.gomoku3.Ids.*
import net.girkin.gomoku3.auth.{PsqlUserRepository, User}
import net.girkin.gomoku3.testutil.{DBTest, IOTest}
import net.girkin.gomoku3.Player
import net.girkin.gomoku3.store.psql.PsqlGameStateQueries
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

class GameStateStoreSpec extends AnyWordSpec with Matchers with IOTest with DBTest {
  val userStore = new PsqlUserRepository(tr)
  val gameStateStore = new GameStateStore(PsqlGameStateQueries, tr)

  "PsqlGameStateStore" should {
    val userOneId = UserId.create
    val userOne = User(userOneId, userOneId.toString + "@test.com", Instant.now())
    val userTwoId = UserId.create
    val userTwo = User(userTwoId, userTwoId.toString + "@test.com", Instant.now())
    val gameState = GameState.create(userOne.id, userTwo.id, GameRules(3, 3, 3))

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
        moveDbRecord <- gameStateStore.insertMove(gameState.gameId, moveMade)
        fetchedGame <- gameStateStore.get(gameState.gameId)
      } yield {
        fetchedGame
      }

      fetchedGameF.unsafeRunSync() shouldBe Some(newGameState)
    }
  }
}


