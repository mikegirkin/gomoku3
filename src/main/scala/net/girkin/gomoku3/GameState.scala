package net.girkin.gomoku3

import net.girkin.gomoku3.Ids.*

import java.time.Instant

case class MoveMade(
  id: MoveId,
  row: Int,
  col: Int,
  player: UserId
)

case class GameState(
  gameId: GameId,
  createdAt: Instant,
  playerOne: UserId,
  playerTwo: UserId,
  game: Game
) {
  def containsPlayer(playerId: UserId) = {
    playerOne == playerId || playerTwo == playerId
  }
  
  def whichPlayer(playerId: UserId): Option[Player] = {
    if(playerOne == playerId) Some(Player.One)
    else if(playerTwo == playerId) Some(Player.Two)
    else None
  }
}

object GameState {
  def create(playerOne: UserId, playerTwo: UserId, rules: GameRules) = {
    new GameState(GameId.create, Instant.now(), playerOne, playerTwo, Game.create(rules))
  }
}
