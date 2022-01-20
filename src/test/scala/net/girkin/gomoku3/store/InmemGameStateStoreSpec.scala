package net.girkin.gomoku3.store

import net.girkin.gomoku3
import net.girkin.gomoku3.*
import net.girkin.gomoku3.testutil.IOTest
import org.scalatest.*
import org.scalatest.matchers.*

import java.time.Instant

class InmemGameStateStoreSpec extends wordspec.AnyWordSpec with should.Matchers with Inside with IOTest {

  "InMemGameStateStore" should {
    "be able to store single game state" in {
      val storeF = InmemGameStateStore.create()
      val rules = GameRules(3, 3, 3)
      val game = Game.create(rules)
      val testGameState = gomoku3.GameState(
        GameId.create,
        Instant.now(),
        PlayerId.create,
        PlayerId.create,
        game
      )
      val resultF = for {
        store <- storeF
        _ <- store.save(testGameState)
        returned <- store.getLatest(testGameState.gameId)
      } yield {
        returned
      }

      val result: Option[GameState] = resultF.unsafeRunSync()

      result shouldBe Some(testGameState)
    }
  }

}
