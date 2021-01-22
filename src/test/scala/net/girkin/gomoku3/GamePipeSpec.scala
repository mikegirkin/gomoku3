package net.girkin.gomoku3

import cats.effect._
import net.girkin.gomoku3.store.InmemGameStateStore
import org.scalatest._
import org.scalatest.matchers._
import net.girkin.gomoku3.testutil.IOTest

import java.time.Instant

class GamePipeSpec extends wordspec.AnyWordSpec with should.Matchers with Inside with IOTest {

  "GamePipe" should {
    "be able to handle messages" in {
      val gameStore = InmemGameStateStore.create().unsafeRunSync()
      val gameId = GameId.create
      val playerOneId = PlayerId.create
      val playerTwoId = PlayerId.create
      val rules = GameRules(3, 3, 3)
      val game = Game.create(rules)
      val pipe = GamePipe.create(gameStore)(gameId, playerOneId, playerTwoId, game)
        .unsafeRunSync()
      
      val testStream = fs2.Stream.emits(List(
        MoveRequest(gameId, playerOneId, 0, 0),
        MoveRequest(gameId, playerTwoId, 1, 1),
        MoveRequest(gameId, playerOneId, 1, 0),
        MoveRequest(gameId, playerTwoId, 1, 2),
        MoveRequest(gameId, playerOneId, 2, 0),
      ))
      
      val result = testStream.through(pipe.pipe)
        .compile
        .toList
        .unsafeRunSync()
      
      result shouldBe List(
        List(GameStateUpdate.MoveMade(gameId, playerOneId, 0, 0)),
        List(GameStateUpdate.MoveMade(gameId, playerTwoId, 1, 1)),
        List(GameStateUpdate.MoveMade(gameId, playerOneId, 1, 0)),
        List(GameStateUpdate.MoveMade(gameId, playerTwoId, 1, 2)),
        List(GameStateUpdate.MoveMade(gameId, playerOneId, 2, 0), GameStateUpdate.GameFinished(gameId))
      )
      
      val gameStateOpt = gameStore.getLatest(gameId).unsafeRunSync()
      
      inside(gameStateOpt) {
        case Some(GameState(gameId, _, playerOneId, playerTwoId, newGameState)) =>
          newGameState.rules shouldBe rules 
          inside(newGameState.field) {
            case GameField(rules.height, rules.width, data) =>
              import Player._
              data shouldBe Vector(
                Vector(CellState.taken(One), CellState.empty, CellState.empty),
                Vector(CellState.taken(One), CellState.taken(Two), CellState.taken(Two)),
                Vector(CellState.taken(One), CellState.empty, CellState.empty)
              )
          }
      }
    }
  }
}
