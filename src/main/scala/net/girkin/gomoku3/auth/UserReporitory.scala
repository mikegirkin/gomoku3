package net.girkin.gomoku3.auth

import cats.*
import cats.implicits.*
import cats.effect.IO
import net.girkin.gomoku3.Ids.UserId
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import doobie.postgres.pgisimplicits.*

import java.time.Instant
import java.util.UUID

trait UserReporitory[F[_]] {
  def getById(userId: UserId): F[Option[User]]

  def getByEmail(email: String): F[Option[User]]

  def insertOrUpdate(user: User): F[Unit]
}

class PsqlUserRepository(transactor: Transactor[IO]) extends UserReporitory[IO] {
  import net.girkin.gomoku3.DoobieIdRepresentations.given

  override def getById(
    userId: UserId
  ): IO[Option[User]] = {
    val result =
      sql"select id, email, created_at from users where id = $userId"
        .query[User].option
    result.transact(transactor)
  }

  override def getByEmail(email: String): IO[Option[User]] = {
    val result =
      sql"select id, email, created_at from users where email = $email"
        .query[User].option
    result.transact(transactor)
  }

  override def insertOrUpdate(user: User): IO[Unit] = {
    val query =
      sql"""insert into users (id, email, created_at)
           |values (${user.id}, ${user.email}, ${user.createdAt})
           |on conflict (id) do
           |update set email = excluded.email""".stripMargin

    query
      .update
      .run
      .transact(transactor)
      .map(_ => ())
  }
}