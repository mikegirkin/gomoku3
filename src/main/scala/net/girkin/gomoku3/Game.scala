package net.girkin.gomoku3

import java.util.UUID

enum Player {
  case One, Two

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

enum MoveAttemptFailure {
  case WrongPlayer
  case ImpossibleMove
  case GameFinished
}

case class Game private(field: GameField, expectingMoveFrom: Player, rules: GameRules) {

  def validateMove(request: GameMoveRequest): Option[MoveAttemptFailure] = {
    if (request.player != expectingMoveFrom) Some(MoveAttemptFailure.WrongPlayer)
    else if (!field.withinBoundaries(request.row, request.column)) Some(MoveAttemptFailure.ImpossibleMove)
    else if (field.get(request.row, request.column) != Option(CellState.empty)) Some(MoveAttemptFailure.ImpossibleMove)
    else None
  }

  def attemptMove(request: GameMoveRequest): Either[MoveAttemptFailure, Game] = {
    validateMove(request).fold {
      val newFieldOpt = field.update(request.row, request.column, CellState.taken(request.player))
      newFieldOpt.fold(
        Left(MoveAttemptFailure.ImpossibleMove)
      ) { newFieldState => 
          val newGameState = this.copy(newFieldState, request.player.other) 
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
    Game(GameField.empty(rules.height, rules.width), Player.One, rules)
  }
}