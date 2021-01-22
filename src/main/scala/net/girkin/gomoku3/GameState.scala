package net.girkin.gomoku3

import java.time.Instant

case class GameState(
  gameId: GameId,
  created: Instant,
  playerOne: PlayerId,
  playerTwo: PlayerId,
  game: Game
) {
  def containsPlayer(playerId: PlayerId) = {
    playerOne == playerId || playerTwo == playerId
  }
  
  def whichPlayer(playerId: PlayerId): Option[Player] = {
    if(playerOne == playerId) Some(Player.One)
    else if(playerTwo == playerId) Some(Player.Two)
    else None
  }
}
