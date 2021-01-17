package net.girkin.gomoku3.store

import net.girkin.gomoku3.GameRules
import org.scalatest._
import org.scalatest.matchers._

import java.time.Instant

class InmemGameStateStoreSpec extends wordspec.AnyWordSpec with should.Matchers with Inside {
  
  "InMemGameStateStore" should {
    "be able to store single game state" in {
      val storeF = InmemGameStateStore.create()
      val testGameState = GameState(
        GameId.create,
        GameRules(3, 3, 3),
        Instant.now(),
        PlayerId.create,
        PlayerId.create,
        Vector.empty
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
