package net.girkin.gomoku3

import org.scalatest._
import org.scalatest.matchers._

class GameFieldSpec extends wordspec.AnyWordSpec with should.Matchers {
  
  "GameField" should {
    "return no win result" in {
      val field = GameField.empty(3, 3)
      val result = field.winResult(3)
      
      result shouldBe None
    }
    
    "return win result" when {
      val field = GameField.empty(5, 5)
      val requiredCount = 3

      "line is horizontal" in {
        val positions = List(
          (2, 2, CellState.taken(Player.One)),
          (2, 3, CellState.taken(Player.One)),
          (2, 4, CellState.taken(Player.One))
        )

        val fieldWithFilledCells = positions.foldLeft(Option(field)) {
          case (Some(field), (r, c, state)) => field.update(r, c, state)
          case _ => None
        }
        val winResult = fieldWithFilledCells.flatMap(_.winResult(requiredCount))
        
        winResult shouldBe Some(WinResult(2, 2, WinLineDirection.Horizontal, Player.One))
      }

      "line is vertical" in {
        val positions = List(
          (2, 2, CellState.taken(Player.One)),
          (3, 2, CellState.taken(Player.One)),
          (4, 2, CellState.taken(Player.One))
        )

        val fieldWithFilledCells = positions.foldLeft(Option(field)) {
          case (Some(field), (r, c, state)) => field.update(r, c, state)
          case _ => None
        }
        val winResult = fieldWithFilledCells.flatMap(_.winResult(requiredCount))

        winResult shouldBe Some(WinResult(2, 2, WinLineDirection.Vertical, Player.One))
      }

      "line is diagonal" in {
        val positions = List(
          (2, 2, CellState.taken(Player.One)),
          (3, 3, CellState.taken(Player.One)),
          (4, 4, CellState.taken(Player.One))
        )

        val fieldWithFilledCells = positions.foldLeft(Option(field)) {
          case (Some(field), (r, c, state)) => field.update(r, c, state)
          case _ => None
        }
        val winResult = fieldWithFilledCells.flatMap(_.winResult(requiredCount))

        winResult shouldBe Some(WinResult(2, 2, WinLineDirection.Diagonal, Player.One))
      }
    }
  }
}
