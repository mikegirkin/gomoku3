package net.girkin.gomoku3.http

import doobie.free.connection
import net.girkin.gomoku3.Ids.GameId
import net.girkin.gomoku3.auth.AuthUser.AuthToken
import net.girkin.gomoku3.auth.{AuthUser, User}
import net.girkin.gomoku3.http.GameRoutesService.AccessError
import net.girkin.gomoku3.store.{GameDBRecord, JoinRequestRecord}
import net.girkin.gomoku3.testutil.TestDataMaker.createTestUser
import net.girkin.gomoku3.testutil.{FullSeviceSetup, IOTest, MockitoScalaSugar}
import net.girkin.gomoku3.{GameRules, GameState}
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GameServiceSpec extends AnyWordSpec with Matchers with MockitoScalaSugar with IOTest {

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

  "getGameState" should {
    "return NotFound when there is no such game" in ioTest {
      val gameId = GameId.create
      val user = createTestUser()
      val token: AuthToken = AuthUser.AuthToken(user.id)

      val setup = new FullSeviceSetup {}
      import setup.*
      when(gameStateQueries.getByIdQuery(gameId))
        .thenReturn(connection.pure(None))

      for {
        result <- gameService.getGameState(token, gameId)
      } yield {
        result shouldBe AccessError.NotFound
      }
    }

    "return AccessDenied when the user requesting is not in the game" in ioTest {
      val userOne = createTestUser()
      val userTwo = createTestUser()
      val game = GameState.create(userOne.id, userTwo.id, GameRules(3, 3, 3))
      val otherUser = createTestUser()
      val token: AuthToken = AuthUser.AuthToken(otherUser.id)
      val gameDBRecord = GameDBRecord.fromGameState(game)

      val setup = new FullSeviceSetup {}
      import setup.*
      when(gameStateQueries.getByIdQuery(game.gameId))
        .thenReturn(connection.pure(Some(gameDBRecord)))
      when(gameStateQueries.getMovesQuery(game.gameId))
        .thenReturn(connection.pure(Vector.empty))

      for {
        result <- gameService.getGameState(token, game.gameId)
      } yield {
        result shouldBe AccessError.AccessDenied
      }
    }

    "return game state in a happy scenario" in ioTest {
      val userOne = createTestUser()
      val userTwo = createTestUser()
      val game = GameState.create(userOne.id, userTwo.id, GameRules(3, 3, 3))
      val token: AuthToken = AuthUser.AuthToken(userOne.id)
      val gameDBRecord = GameDBRecord.fromGameState(game)

      val setup = new FullSeviceSetup {}
      import setup.*
      when(gameStateQueries.getByIdQuery(game.gameId))
        .thenReturn(connection.pure(Some(gameDBRecord)))
      when(gameStateQueries.getMovesQuery(game.gameId))
        .thenReturn(connection.pure(Vector.empty))

      for {
        result <- gameService.getGameState(token, game.gameId)
      } yield {
        result shouldBe game
      }
    }
  }
}
