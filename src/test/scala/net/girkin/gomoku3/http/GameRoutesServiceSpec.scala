package net.girkin.gomoku3.http

import doobie.free.connection
import net.girkin.gomoku3.Ids.{GameId, MoveId}
import net.girkin.gomoku3.auth.AuthUser.AuthToken
import net.girkin.gomoku3.auth.{AuthUser, User}
import net.girkin.gomoku3.http.GameRoutesService.AccessError
import net.girkin.gomoku3.store.{GameDBRecord, GameStateStore, JoinRequestRecord, MoveDbRecord}
import net.girkin.gomoku3.testutil.TestDataMaker.{createTestGameWithUsers, createTestUser}
import net.girkin.gomoku3.testutil.{FullSeviceSetup, IOTest, MockitoScalaSugar}
import net.girkin.gomoku3.{GameMoveRequest, GameRules, GameState, MoveAttemptFailure, Player}
import net.girkin.gomoku3.http.GameRoutesService.*
import org.mockito.Mockito.when
import org.scalatest.Inside
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

class GameRoutesServiceSpec extends AnyWordSpec with Matchers with MockitoScalaSugar with IOTest with Inside {

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
      val (activeGame, userOne, userTwo) = createTestGameWithUsers()
      val userOneAuthToken: AuthToken = AuthUser.AuthToken(userOne.id)
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
    val (game, userOne, userTwo) = createTestGameWithUsers()
    val token: AuthToken = AuthUser.AuthToken(userOne.id)
    val otherUser = createTestUser()
    val otherUserToken: AuthToken = AuthUser.AuthToken(otherUser.id)

    def setupMocks(
      getByIdQueryReturns: Option[GameDBRecord] = Some(GameDBRecord.fromGameState(game))
    ) = {
      new FullSeviceSetup {
        when(gameStateQueries.getByIdQuery(game.gameId))
          .thenReturn(connection.pure(getByIdQueryReturns))
        when(gameStateQueries.getMovesQuery(game.gameId))
          .thenReturn(connection.pure(Vector.empty))
      }
    }

    "return NotFound when there is no such game" in ioTest {
      val setup = setupMocks(getByIdQueryReturns = None)
      import setup.*

      for {
        result <- gameService.getGameState(token, game.gameId)
      } yield {
        result shouldBe AccessError.NotFound
      }
    }

    "return AccessDenied when the user requesting is not in the game" in ioTest {
      val setup = setupMocks()
      import setup.*
      for {
        result <- gameService.getGameState(otherUserToken, game.gameId)
      } yield {
        result shouldBe AccessError.AccessDenied
      }
    }

    "return game state in a happy scenario" in ioTest {
      val setup = setupMocks()
      import setup.*
      for {
        result <- gameService.getGameState(token, game.gameId)
      } yield {
        result shouldBe game
      }
    }
  }

  "postMove" should {
    val (game, userOne, userTwo) = createTestGameWithUsers()
    val otherUser = createTestUser()
    val moves = Vector(
      MoveDbRecord(MoveId.create, game.gameId, 0, Instant.now(), 1, 1, Player.One)
    )

    def setupMocks(
      getByIdQueryReturns: Option[GameDBRecord] = Some(GameDBRecord.fromGameState(game)),
      getMovesQueryReturns: Vector[MoveDbRecord] = moves
    ) = {
      new FullSeviceSetup {
        when(gameStateQueries.getByIdQuery(game.gameId))
          .thenReturn(connection.pure(getByIdQueryReturns))
        when(gameStateQueries.getMovesQuery(game.gameId))
          .thenReturn(connection.pure(getMovesQueryReturns))
      }
    }

    "return NotFound if there is no such game" in ioTest {
      val setup = setupMocks(
        getByIdQueryReturns = None
      )
      import setup.*
      for {
        result <- gameService.postMove(MoveRequest(game.gameId, userOne.id, 1, 1))
      } yield {
        result shouldBe AccessError.NotFound
      }
    }

    "return AccessDenied if the user is not in the game requested" in ioTest {
      val setup = setupMocks()
      import setup.*
      for {
        result <- gameService.postMove(MoveRequest(game.gameId, otherUser.id, 1, 1))
      } yield {
        result shouldBe AccessError.AccessDenied
      }
    }

    "return MoveAttemptFailure.WrongPlayer when incorrect player attempts to make move" in ioTest {
      val setup = setupMocks()
      import setup.*
      for {
        result <- gameService.postMove(MoveRequest(game.gameId, userOne.id, 0, 0))
      } yield {
        result shouldBe MoveAttemptFailure.WrongPlayer
      }
    }

    "return MoveAttemptFailure.ImpossibleMove" when {
      val setup = setupMocks()
      import setup.*

      "cell is already taken" in ioTest {
        for {
          result <- gameService.postMove(MoveRequest(game.gameId, userTwo.id, 1, 1))
        } yield {
          result shouldBe MoveAttemptFailure.ImpossibleMove
        }
      }

      "requested move is outside of the boundaries" in ioTest {
        for {
          result <- gameService.postMove(MoveRequest(game.gameId, userTwo.id, 20, 20))
        } yield {
          result shouldBe MoveAttemptFailure.ImpossibleMove
        }
      }
    }

    "return MoveMade in the happy path" in ioTest {
      val setup = setupMocks()
      import setup.*
      val moveRequest = MoveRequest(game.gameId, userTwo.id, 0, 0)
      for {
        result <- gameService.postMove(moveRequest)
      } yield {
        inside(result) {
          case MoveRequestFulfilled(moveId, request) => request shouldBe moveRequest
        }
      }
    }
  }

  "getMoves" should {
    val (gameState, userOne, userTwo) = createTestGameWithUsers()
    val moveDbRecords = Vector(
      MoveDbRecord.create(gameState.gameId, 0, 0, 0, Player.One),
      MoveDbRecord.create(gameState.gameId, 1, 0, 1, Player.Two),
      MoveDbRecord.create(gameState.gameId, 2, 1, 0, Player.One),
      MoveDbRecord.create(gameState.gameId, 3, 1, 1, Player.Two)
    )
    val otherUser = createTestUser()

    def setupMocks(
      getByIdQueryReturns: Option[GameDBRecord] = Some(GameDBRecord.fromGameState(gameState)),
      getMovesQueryReturns: Vector[MoveDbRecord] = moveDbRecords
    ) = {
      new FullSeviceSetup {
        when(gameStateQueries.getByIdQuery(gameState.gameId))
          .thenReturn(connection.pure(getByIdQueryReturns))
        when(gameStateQueries.getMovesQuery(gameState.gameId))
          .thenReturn(connection.pure(getMovesQueryReturns))
      }
    }

    "return NotFound if there is no such game" in ioTest {
      val setup = setupMocks(
        getByIdQueryReturns = None
      )
      import setup.*

      for {
        result <- gameService.getMoves(userOne.id, gameState.gameId)
      } yield {
        result shouldBe AccessError.NotFound
      }
    }

    "return AccessDenied if the user is not in the game requested" in ioTest {
      val setup = setupMocks()
      import setup.*

      for {
        result <- gameService.getMoves(otherUser.id, gameState.gameId)
      } yield {
        result shouldBe AccessError.AccessDenied
      }
    }

    "return Moves made in the game" in ioTest {
      val setup = setupMocks()
      import setup.*

      for {
        result <- gameService.getMoves(userOne.id, gameState.gameId)
      } yield {
        result shouldBe moveDbRecords
      }
    }

  }
}
