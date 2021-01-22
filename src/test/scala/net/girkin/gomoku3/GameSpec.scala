package net.girkin.gomoku3

import org.scalatest._
import org.scalatest.matchers._

class GameSpec extends wordspec.AnyWordSpec with should.Matchers with Inside {
  "Game" should {
    val game = Game.create(GameRules(3, 3, 3))
    
    "reject move" when {
      "wrong player tries" in {
        val attempt = GameMoveRequest(0, 0, Player.Two)
        val result = game.attemptMove(attempt)
        result shouldBe Left(MoveAttemptFailure.WrongPlayer)
      }
      
      "move is out of bounds" in {
        val attempt = GameMoveRequest(0, 5, Player.One)
        val result = game.attemptMove(attempt)
        result shouldBe Left(MoveAttemptFailure.ImpossibleMove)
      }
      
      "attempts to move into place already taken" in {
        val moveOne = GameMoveRequest(0, 0, Player.One)
        val gameAfterMoveOne = game.attemptMove(moveOne).toOption.get
        val attempt = GameMoveRequest(0, 0, Player.Two)
        val result = gameAfterMoveOne.attemptMove(attempt)
        result shouldBe Left(MoveAttemptFailure.ImpossibleMove)
      }
    }
    
    "accept move" in {
      val attempt = GameMoveRequest(0, 0, Player.One)
      val result = game.attemptMove(attempt)
      inside(result) {
        case Right(game) =>
          game.expectingMoveFrom shouldBe Player.Two
      }
    }
    
    "be able to detect winner" in {
      val moves = List(
        GameMoveRequest(0, 0, Player.One),
        GameMoveRequest(0, 1, Player.Two),
        GameMoveRequest(1, 0, Player.One),
        GameMoveRequest(0, 2, Player.Two),
        GameMoveRequest(2, 0, Player.One)
      )
      val gameStateAfterMoves = moves.foldLeft(Vector(game)) {
        case (stateList, move) => 
          val newState = stateList.last.attemptMove(move)
          stateList.appended(newState.toOption.get)
      }
      val gameWinState = gameStateAfterMoves.map(_.winResult)
      
      gameWinState should contain theSameElementsInOrderAs List(
        None,
        None,
        None, 
        None,
        None,
        Some(WinResult(0, 0, WinLineDirection.Vertical, Player.One))
      )
      
    }
  }
}

