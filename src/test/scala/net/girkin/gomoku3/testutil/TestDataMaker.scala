package net.girkin.gomoku3.testutil

import net.girkin.gomoku3.Ids.UserId
import net.girkin.gomoku3.auth.User

import java.time.Instant

object TestDataMaker {
  def createTestUser(): User = {
    val userId = UserId.create
    User(userId, s"${userId}@test.com", Instant.now())
  }
}
