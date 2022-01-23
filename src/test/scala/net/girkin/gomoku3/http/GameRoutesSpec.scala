package net.girkin.gomoku3.http

import cats.effect.IO
import net.girkin.gomoku3.auth.{CookieAuth, PrivateKey}
import net.girkin.gomoku3.testutil.IOTest
import org.http4s.*
import org.http4s.headers.Location
import org.http4s.implicits.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.mockito.Mockito.*
import org.typelevel.ci.CIString

class GameRoutesSpec extends AnyWordSpec with Matchers with IOTest {

  "/games" should {
    val key = PrivateKey(Array(1))
    val loginPageUri = uri"/auth/login"
    val auth = new CookieAuth[IO](key, loginPageUri)
    val gameService = mock(classOf[GameRoutesService])
    val service = new GameRoutes(auth, gameService).routes.orNotFound

    "return 303 when got a browser request with no token" in {
      val request = Request[IO](uri = uri"/games")

      val response: Response[IO] = service.run(request).unsafeRunSync()

      response.status shouldBe Status.SeeOther
      response.headers.get[Location].get.uri shouldBe loginPageUri
    }

    "return 403 when got AJAX request with no token" in {
      val request = Request[IO](uri = uri"/games")
        .putHeaders(Header.Raw(CIString("X-Requested-With"), "XMLHttpRequest"))

      val response: Response[IO] = service.run(request).unsafeRunSync()

      response.status shouldBe Status.Forbidden
    }
  }
}
