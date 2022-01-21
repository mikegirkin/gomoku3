package net.girkin.gomoku3.auth

import cats.effect.IO
import doobie.util.transactor.Transactor
import net.girkin.gomoku3.testutil.IOTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class PsqlUserRepositoryItTest extends AnyWordSpec with Matchers with IOTest {
  val xa = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",     // driver classname
    "jdbc:postgresql://localhost:5432/gomoku3"     // connect URL (driver-specific)
  )

  val repo = new PsqlUserRepository(xa)

  "PsqlUserRepository" should {
    val email = UUID.randomUUID().toString + "@test.com"
    val user = User.create(email)

    "be able to insert a user" in {
      val result = repo.insertOrUpdate(user)
        .unsafeRunSync()

      result shouldBe ()
    }

    "be able to retrieve user back by Id" in {
      val fetched = repo.getById(user.userId).unsafeRunSync()

      fetched shouldBe Some(user)
    }

    "be able to retrieve user back by email" in {
      val fetched = repo.getByEmail(user.email).unsafeRunSync()

      fetched shouldBe Some(user)
    }

    "be able to update user email" in {
      val updatedUser = user.copy(email = "UPDATED" + user.email)
      repo.insertOrUpdate(updatedUser).unsafeRunSync()

      val fetched = repo.getById(user.userId).unsafeRunSync()

      fetched shouldBe Some(updatedUser)
    }
  }
}
