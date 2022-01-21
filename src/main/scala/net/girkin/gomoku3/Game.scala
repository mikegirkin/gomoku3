package net.girkin.gomoku3

import net.girkin.gomoku3.Ids.{MoveId, UserId}

import java.util.UUID

enum Player(val value: Int) {
  case One extends Player(1)
  case Two extends Player(2)

  def other = {
    if(this == One) Two else One
  }
}

case class GameRules(
  height: Int,
  width: Int,
  winCondition: Int
)

case class GameMoveRequest(
  row: Int,
  column: Int,
  player: Player
)

case class MoveMade(
  moveId: MoveId,
  row: Int,
  col: Int,
  player: Player
)

enum MoveAttemptFailure {
  case WrongPlayer
  case ImpossibleMove
  case GameFinished
}

final case class Game private(
  field: GameField,
  expectingMoveFrom: Player,
  rules: GameRules,
  movesMade: Vector[MoveMade]
) {

  private def validateMove(request: MoveMade): Option[MoveAttemptFailure] = {
    if (request.player != expectingMoveFrom) Some(MoveAttemptFailure.WrongPlayer)
    else if (!field.withinBoundaries(request.row, request.col)) Some(MoveAttemptFailure.ImpossibleMove)
    else if (field.get(request.row, request.col) != Option(CellState.empty)) Some(MoveAttemptFailure.ImpossibleMove)
    else None
  }

  def attemptMove(request: GameMoveRequest): Either[MoveAttemptFailure, Game] = {
    val moveMade = MoveMade(MoveId.create, request.row, request.column, request.player)
    executeMoveMade(moveMade)
  }

  def executeMoveMade(move: MoveMade): Either[MoveAttemptFailure, Game] = {
    validateMove(move).fold {
      val newFieldOpt = field.update(move.row, move.col, CellState.taken(move.player))
      newFieldOpt.fold(
        Left(MoveAttemptFailure.ImpossibleMove)
      ) { newFieldState =>
        val newGameState = this.copy(newFieldState, this.expectingMoveFrom.other, movesMade = this.movesMade.appended(move))
        Right(newGameState)
      }
    } { error =>
      Left(error)
    }
  }
  
  def winResult: Option[WinResult] = {
    field.winResult(rules.winCondition)
  }
}

object Game {
  def create(rules: GameRules) = {
    Game(GameField.empty(rules.height, rules.width), Player.One, rules, Vector.empty)
  }
}