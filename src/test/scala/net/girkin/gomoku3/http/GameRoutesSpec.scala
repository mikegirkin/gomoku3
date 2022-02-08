package net.girkin.gomoku3.http

import cats.effect.*
import net.girkin.gomoku3.testutil.{FullSeviceSetup, IOTest, MockitoScalaSugar}
import org.http4s.*
import org.http4s.headers.Location
import org.http4s.implicits.*
import org.mockito.Mockito.{mock as _, *}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIString

class GameRoutesSpec extends AnyWordSpec with Matchers with MockitoScalaSugar with IOTest {

  "/games" should {
    val setup = new FullSeviceSetup {}
    import setup.*

    val service = gameRoutes.routes.orNotFound

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
