package net.girkin.gomoku3.http

import cats.*
import cats.effect.*
import cats.effect.unsafe.IORuntime
import cats.implicits.*
import doobie.*
import doobie.free.connection
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import doobie.postgres.pgisimplicits.*
import doobie.util.transactor.{Strategy, Transactor}
import io.circe.Json
import net.girkin.gomoku3.Ids.{JoinGameRequestId, UserId}
import net.girkin.gomoku3.Ops.*
import net.girkin.gomoku3.auth.AuthUser.AuthToken
import net.girkin.gomoku3.auth.{AuthUser, CookieAuth, PrivateKey, User}
import net.girkin.gomoku3.http.Codecs.given
import net.girkin.gomoku3.store.*
import net.girkin.gomoku3.testutil.TestDataMaker.createTestUser
import net.girkin.gomoku3.testutil.{FullSeviceSetup, IOTest, MockitoScalaSugar}
import net.girkin.gomoku3.{GameRules, GameState}
import org.http4s.*
import org.http4s.Status.Ok
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.headers.Location
import org.http4s.implicits.*
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers.{any, argThat}
import org.mockito.Mockito.{mock as _, *}
import org.mockito.internal.stubbing.answers.ThrowsException
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIString

import scala.reflect.ClassTag

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

  "joinRandomGame" should {
    import GameRoutesService.JoinGameError

    val user: User = createTestUser()
    val authToken: AuthToken = AuthUser.AuthToken(user.id)

    "create a new player queue record" in {
      val setup = new FullSeviceSetup {
        when(joinGameRequestQueries.insertJoinGameRequestQuery(argWhere {
          case JoinRequestRecord(_, argUserId, _) => argUserId == user.id
        })).thenReturn(
          connection.pure(JoinRequestRecord.create(user.id))
        )
        when(joinGameRequestQueries.openedJoinRequestQuery())
          .thenReturn(connection.pure(Vector.empty))
      }
      import setup.*

      for {
        result <- gameService.joinRandomGame(authToken)
      } yield {
        result shouldBe Right(())
      }
    }

    "prevent joining a second game if user is in an active game" in ioTest {
      val otherUser: User = createTestUser()
      val activeGame = GameState.create(user.id, otherUser.id, GameRules(3, 3, 3))

      val setup = new FullSeviceSetup {
        when(gameStateQueries.getForUserQuery(user.id, Some(true)))
          .thenReturn(connection.pure(Vector(activeGame)))
      }
      import setup.*

      for {
        result <- gameService.joinRandomGame(authToken)
      } yield {
        result shouldBe Left(JoinGameError.UserAlreadyInActiveGame)
      }
    }

    "prevent creating join request if there is already one" in ioTest {
      val existingRequest: JoinRequestRecord = JoinRequestRecord.create(user.id)
      val setup = new FullSeviceSetup {
        when(gameStateQueries.getForUserQuery(user.id, Some(true)))
          .thenReturn(connection.pure(Vector.empty))
        when(joinGameRequestQueries.getActiveForUser(user.id))
          .thenReturn(connection.pure(Vector(JoinRequestRecord.create(user.id))))
      }
      import setup.*

      for {
        result <- gameService.joinRandomGame(authToken)
      } yield {
        result shouldBe Left(JoinGameError.UserAlreadyQueued)
      }
    }
  }

  "listGames" should {
    trait Setup extends FullSeviceSetup {
      val userOneAuthToken: AuthToken
      val allGamesInStore: Vector[GameDBRecord]
      val activeGamesInStore: Vector[GameDBRecord]
    }

    def setup() = new Setup {
      val userOne = User.create("user1@test.com")
      val userTwo = User.create("user2@test.com")
      val userOneAuthToken: AuthToken = AuthUser.AuthToken(userOne.id)
      val activeGame = GameState.create(userOne.id, userTwo.id, ticTacToeRules)
      val inactiveGame = GameState.create(userOne.id, userTwo.id, ticTacToeRules)
      val activeGamesInStore = Vector(GameDBRecord.fromGameState(activeGame))
      val allGamesInStore = Vector(GameDBRecord.fromGameState(activeGame), GameDBRecord.fromGameState(inactiveGame))

      when(gameStateQueries.getForUserQuery(userOne.id, Some(true)))
        .thenReturn(connection.pure(activeGamesInStore))
      when(gameStateQueries.getForUserQuery(userOne.id, None))
        .thenReturn(connection.pure(allGamesInStore))
    }


    "return active game for the user if there is any and active = Some(true)" in ioTest {
      val env = setup()
      import env.*

      for {
        result <- env.gameService.listGames(userOneAuthToken, Some(true))
      } yield {
        result shouldBe activeGamesInStore
      }
    }

    "return all games for the user if active = None" in ioTest {
      val env = setup()
      import env.*

      for {
        result <- gameService.listGames(userOneAuthToken, None)
      } yield {
        result should contain theSameElementsAs allGamesInStore
      }
    }
  }
}
