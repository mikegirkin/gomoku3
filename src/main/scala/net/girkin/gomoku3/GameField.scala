package net.girkin.gomoku3

opaque type CellState = Option[Player]

object CellState {
  def empty: CellState = None
  def taken(player: Player): CellState = Some(player)
  def fromOption(state: Option[Player]): CellState = state

  extension (cellState: CellState) {
    def asOption: Option[Player] = cellState
  }
}

enum WinLineDirection {
  case Horizontal
  case Vertical
  case Diagonal
}

case class WinResult(
  startRow: Int,
  startCol: Int,
  direction: WinLineDirection,
  player: Player
)

case class GameField private (height: Int, width: Int, private val values: Vector[Vector[CellState]]) {
  
  def withinBoundaries(row: Int, col: Int): Boolean = {
    if ((row >= 0 && row < height) && (col >= 0) && (col < width)) true else false 
  }

  def get(row: Int, col: Int): Option[CellState] = {
    if(withinBoundaries(row, col)) Some(values(row)(col)) else None
  }

  def update(row: Int, col: Int, cellState: CellState): Option[GameField] = {
    if(withinBoundaries(row, col)) {
      val updatedField = values.updated(row, values(row).updated(col, cellState))
      Some(this.copy(values = updatedField))
    } else {
      None
    }
  }
  
  def winResult(requiredCount: Int): Option[WinResult] = {
    val possibleCompletedLines: IndexedSeq[Option[WinResult]] = for {
      startRow <- Range(0, height - requiredCount + 1)
      startCol <- Range(0, width - requiredCount + 1)
    } yield {
      for {
        lineDirection <- isThereLineStartingAt(startRow, startCol, requiredCount)
        lineOwner <- values(startRow)(startCol).asOption
      } yield {
        WinResult(startRow, startCol, lineDirection, lineOwner)
      }
    }
    possibleCompletedLines.collectFirst {
      case Some(winResult) => winResult
    }
  }
  
  private def isThereLineStartingAt(row: Int, col: Int, count: Int): Option[WinLineDirection] = {
    val cellAtStart = this.get(row, col)
    val horizontalLineCoords = Range(0, count).map(i => (row, col + i))
    val verticalLineCoords  = Range(0, count).map(i => (row + i, col))
    val diagonalLineCoords  = Range(0, count).map(i => (row + i, col + i))
    //hor
    val checks = List(
      WinLineDirection.Horizontal -> horizontalLineCoords,
      WinLineDirection.Vertical -> verticalLineCoords,
      WinLineDirection.Diagonal -> diagonalLineCoords
    )
    val isThereLine = checks.map { case (direction, coords) =>
      val lineExists: Boolean = coords.foldLeft(true) {
        case (result, (r, c)) =>
          val cellUnderInspection = this.get(r, c)
          if(cellUnderInspection == Some(CellState.empty)
            || cellUnderInspection != cellAtStart
            || cellUnderInspection == None) {
            false
          } else {
            true
          }
      }
      direction -> lineExists
    }
    
    isThereLine.collectFirst {
      case (lineDirection, true) => lineDirection 
    }
  }
}

object GameField {
  def empty(width: Int, height: Int) = {
    GameField(height, width, Vector.fill(height, width)(CellState.empty))
  }
}
