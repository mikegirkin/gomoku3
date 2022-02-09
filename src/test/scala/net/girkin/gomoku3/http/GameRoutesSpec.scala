package net.girkin.gomoku3.http

import cats.effect.*
import net.girkin.gomoku3.Ids.GameId
import net.girkin.gomoku3.auth.AuthUser
import net.girkin.gomoku3.testutil.TestDataMaker.createTestUser
import net.girkin.gomoku3.testutil.*
import GameRoutesService.AccessError
import net.girkin.gomoku3.{GameRules, GameState}
import net.girkin.gomoku3.auth.AuthUser.AuthToken
import org.http4s.*
import org.http4s.dsl.io.GET
import org.http4s.headers.Location
import org.http4s.implicits.*
import org.http4s.circe.*
import org.mockito.Mockito.{mock as _, *}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIString
import Codecs.given
import io.circe.*
import io.circe.syntax.*

class GameRoutesSpec extends AnyWordSpec with Matchers with MockitoScalaSugar with IOTest with HttpAuth {

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

  "/games/:id" should {
    val user = createTestUser()
    val token: AuthToken = AuthUser.AuthToken(user.id)
    val gameId = GameId.create
    val request = Request[IO](method = GET, uri = uri"/games" / gameId.toUUID)

    "return 404 if there is no such game" in ioTest {
      val env = new RoutesSetup {
        when(gameService.getGameState(token, gameId))
          .thenReturn(IO.pure(AccessError.NotFound))
      }
      import env.*

      for {
        authedRequest <- setRequestAuthCookie(auth)(request, user.id)
        response <- service.run(authedRequest)
      } yield {
        response.status shouldBe Status.NotFound
      }
    }

    "return 403 if the user is not in the game" in ioTest {
      val env = new RoutesSetup {
        when(gameService.getGameState(token, gameId))
          .thenReturn(IO.pure(AccessError.AccessDenied))
      }
      import env.*

      for {
        authedRequest <- setRequestAuthCookie(auth)(request, user.id)
        response <- service.run(authedRequest)
      } yield {
        response.status shouldBe Status.Forbidden
      }
    }

    "return game state when on the happy path" in ioTest {
      val otherUser = createTestUser()
      val gameState = GameState.create(user.id, otherUser.id, GameRules(3, 3, 3))
      val env = new RoutesSetup {
        when(gameService.getGameState(token, gameId))
          .thenReturn(IO.pure(gameState))
      }
      import env.*

      for {
        authedRequest <- setRequestAuthCookie(auth)(request, user.id)
        response: Response[IO] <- service.run(authedRequest)
        receivedJson <- response.as[Json]
      } yield {
        response.status shouldBe Status.Ok
        receivedJson shouldBe gameState.asJson
      }
    }
  }
}
