package net.girkin.gomoku3

import net.girkin.gomoku3.store.InmemGameStateStore
import org.scalatest._
import org.scalatest.matchers._
import testutil._
import net.girkin.gomoku3.Ids.*

class GamePipesSpec extends wordspec.AnyWordSpec with should.Matchers with Inside with IOTest {
  "GamePipes" should {
    val gameId = GameId.create
    val game = Game.create(GameRules(3, 3, 3))
    val gameStateStore = InmemGameStateStore.create().unsafeRunSync()
    val pipe = GamePipe.create(gameStateStore)(gameId, PlayerId.create, PlayerId.create, game).unsafeRunSync()

    val gamePipes = GamePipes.create().unsafeRunSync()
    
    "be able to add a new Pipe" in ioTest {
      for {
        result <- gamePipes.registerGamePipe(gameId, pipe) 
      } yield {
        result shouldBe pipe
      }
    }
    
    "be able to return added Pipe" in {
      for {
        result <- gamePipes.getGamePipe(gameId)
      } yield {
        result shouldBe Some(pipe)
      }
    }
    
    "be able to delete pipe" in {
      for {
        removalResult <- gamePipes.removeGamePipe(gameId)
        result <- gamePipes.getGamePipe(gameId)
      } yield {
        result shouldBe None
      }
    }
  }
}


