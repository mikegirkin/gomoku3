package net.girkin.gomoku3.http

import cats.effect.*
import cats.implicits.*
import net.girkin.gomoku3.{GameRules, GameState, MoveAttemptFailure, Player}
import net.girkin.gomoku3.Ids.{GameId, MoveId}
import net.girkin.gomoku3.auth.AuthUser
import net.girkin.gomoku3.testutil.TestDataMaker.createTestUser
import net.girkin.gomoku3.testutil.*
import GameRoutesService.{AccessError, MoveRequest, MoveRequestFulfilled, PostMoveResult}
import net.girkin.gomoku3.auth.AuthUser.AuthToken
import org.http4s.*
import org.http4s.client.dsl.io.*
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
import net.girkin.gomoku3.store.MoveDbRecord
import org.http4s.Method.{GET, POST}

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
    val request = GET(uri"/games" / gameId.toUUID)

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

  "POST /games/:id/moves" should {
    val user = createTestUser()
    val token: AuthToken = AuthUser.AuthToken(user.id)
    val gameId = GameId.create
    val request = POST(RowCol(1, 1).asJson, uri"/games" / gameId.toUUID / "moves")
    val moveRequest = MoveRequest(gameId, user.id, 1, 1)
    val moveId = MoveId.create
    val fulfilled = MoveRequestFulfilled(moveId, moveRequest)

    val tests = Map[PostMoveResult, (Response[IO], Option[Json]) => Unit](
      AccessError.NotFound -> { (response, json) =>
        response.status shouldBe Status.NotFound
      },
      AccessError.AccessDenied -> { (response, json) =>
        response.status shouldBe Status.Forbidden
      },
      MoveAttemptFailure.ImpossibleMove -> { (response, json) =>
        response.status shouldBe Status.UnprocessableEntity
      },
      fulfilled -> { (response, json) =>
        response.status shouldBe Status.Created
        json.get shouldBe fulfilled.asJson
      },
    )

    def runTest(gameServiceReturns: PostMoveResult, check: (Response[IO], Option[Json]) => Unit) = {
      s"Pass test for ${gameServiceReturns}" in ioTest {
        val env = new RoutesSetup {
          when(gameService.postMove(moveRequest))
            .thenReturn(IO.pure(gameServiceReturns))
        }
        import env.*

        for {
          authedRequest <- setRequestAuthCookie(auth)(request, user.id)
          response: Response[IO] <- service.run(authedRequest)
          responseStr <- response.as[String]
        } yield {
          val json = if(responseStr.isEmpty) None else {
            Some(io.circe.parser.parse(responseStr).toOption.get)
          }
          check(response, json)
        }
      }
    }

    tests.foreach(runTest)
  }

  "GET /games/:id/moves" should {
    val gameId = GameId.create
    val user = createTestUser()
    val request = GET(uri"/games" / gameId.toUUID / "moves")
    val moves = Vector(
      MoveDbRecord.create(gameId, 0, 0, 0, Player.One),
      MoveDbRecord.create(gameId, 1, 0, 1, Player.Two),
      MoveDbRecord.create(gameId, 2, 1, 0, Player.One),
      MoveDbRecord.create(gameId, 3, 1, 1, Player.Two)
    )
    val tests = Map[AccessError | Vector[MoveDbRecord], (Response[IO], Option[Json]) => Unit](
      AccessError.NotFound -> { (response, json) =>
        response.status shouldBe Status.NotFound
      },
      AccessError.AccessDenied -> { (response, json) =>
        response.status shouldBe Status.Forbidden
      },
      moves -> { (response, json) =>
        response.status shouldBe Status.Ok
        json.get shouldBe moves.asJson
      },
    )

    def runTest(gameServiceReturns: AccessError | Vector[MoveDbRecord], check: (Response[IO], Option[Json]) => Unit) = {
      s"Pass test for ${gameServiceReturns}" in ioTest {
        val env = new RoutesSetup {
          when(gameService.getMoves(user.id, gameId))
            .thenReturn(IO.pure(gameServiceReturns))
        }
        import env.*

        for {
          authedRequest <- setRequestAuthCookie(auth)(request, user.id)
          response <- service.run(authedRequest)
          responseStr <- response.as[String]
        } yield {
          val json = if (responseStr.isEmpty) None else {
            Some(io.circe.parser.parse(responseStr).toOption.get)
          }
          check(response, json)
        }
      }
    }

    tests.foreach(runTest)
  }
}
