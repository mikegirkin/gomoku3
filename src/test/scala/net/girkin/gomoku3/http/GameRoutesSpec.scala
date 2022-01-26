package net.girkin.gomoku3.http

import cats.*
import cats.implicits.*
import cats.effect.*
import cats.effect.unsafe.IORuntime
import net.girkin.gomoku3.GameRules
import net.girkin.gomoku3.Ids.{JoinGameRequestId, UserId}
import net.girkin.gomoku3.auth.{AuthUser, CookieAuth, PrivateKey}
import net.girkin.gomoku3.testutil.IOTest
import org.http4s.*
import org.http4s.headers.Location
import org.http4s.implicits.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import doobie.postgres.pgisimplicits.*
import doobie.free.connection
import doobie.util.transactor.{Strategy, Transactor}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.mockito.Mockito.*
import org.typelevel.ci.CIString
import net.girkin.gomoku3.Ops.*
import net.girkin.gomoku3.auth.AuthUser.AuthToken
import net.girkin.gomoku3.store.{GameEventQueries, GameStateQueries, GameStateStore, JoinGameRequestQueries, JoinGameService, JoinRequestRecord}
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers.{any, argThat}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

class GameRoutesSpec extends AnyWordSpec with Matchers {

  private def argWhere[T](predicate: PartialFunction[T, Boolean]) = argThat {
    new ArgumentMatcher[T] {
      override def matches(argument: T): Boolean = {
        predicate.lift.apply(argument).getOrElse(false)
      }
    }
  }

  implicit val runtime: IORuntime = cats.effect.unsafe.implicits.global

  val privateKeyStr = "sadflwqerousadv123"
  val privateKey = PrivateKey(scala.io.Codec.toUTF8(privateKeyStr))
  val loginPageUri = uri"/auth/login"
  val auth = new CookieAuth[IO](privateKey, loginPageUri)

  "/games" should {
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

  "/games/joinRandom" should {
    "create a new player queue record" in {
      import org.http4s.dsl.io._

      val transactor = Transactor(
        (),
        (_: Unit) => Resource.pure(null),
        KleisliInterpreter[IO].ConnectionInterpreter,
        Strategy.void
      )

      val userId = UserId.create
      val authToken: AuthToken = AuthUser.AuthToken(userId)
      val rules = GameRules(3, 3, 3)

      val gameStateQueries = mock(classOf[GameStateQueries], (invocation: InvocationOnMock) => ???)
      val gameStateStore = new GameStateStore(gameStateQueries, transactor)
      val joinGameRequestQueries = mock(classOf[JoinGameRequestQueries])
      when(joinGameRequestQueries.insertJoinGameRequestQuery(argWhere {
        case JoinRequestRecord(_, argUserId, _) => argUserId == userId
      })).thenReturn(
        connection.pure(JoinRequestRecord.create(userId))
      )
      when(joinGameRequestQueries.openedJoinRequestQuery())
        .thenReturn(connection.pure(Vector.empty))

      val gameEventQueries = mock(classOf[GameEventQueries], (invocation: InvocationOnMock) => ???)

      val joinGameService = new JoinGameService(gameStateQueries, gameEventQueries, joinGameRequestQueries, transactor)
      val gameService = new GameRoutesService(gameStateStore, joinGameService, rules)
      val service = new GameRoutes(auth, gameService).routes.orNotFound

      val responseF = for {
        token <- auth.signToken(authToken)
        request = Request[IO](Method.POST, uri = uri"/games/joinRandom")
          .addCookie(auth.authCookieName, token)
        response <- service.run(request)
      } yield {
        response
      }

      val response = responseF.unsafeRunSync()

      response.status shouldBe Accepted
    }
  }
}
