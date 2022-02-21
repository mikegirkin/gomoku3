package net.girkin.gomoku3.testutil

import net.girkin.gomoku3.{GameRules, GameState}
import net.girkin.gomoku3.Ids.UserId
import net.girkin.gomoku3.auth.User

import java.time.Instant

object TestDataMaker {
  def createTestUser(): User = {
    val userId = UserId.create
    User(userId, s"${userId}@test.com", Instant.now())
  }

  def createTestGameWithUsers(): (GameState, User, User) = {
    val userOne = createTestUser()
    val userTwo = createTestUser()
    val game = GameState.create(userOne.id, userTwo.id, GameRules(3, 3, 3))
    (game, userOne, userTwo)
  }
}
