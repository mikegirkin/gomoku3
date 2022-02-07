package net.girkin.gomoku3.auth

import cats.effect.IO
import net.girkin.gomoku3.Ids.UserId

import java.time.Instant

case class User(
  id: UserId,
  email: String,
  createdAt: Instant
)

object User {
  def create(email: String) = User(UserId.create, email, Instant.now())
}
