package net.girkin.gomoku3.store

import net.girkin.gomoku3.{Game, GameRules, GameState}
import net.girkin.gomoku3.testutil.{DBTest, IOTest}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import net.girkin.gomoku3.Ids.*
import net.girkin.gomoku3.auth.{PsqlUserRepository, User}
import net.girkin.gomoku3.store.psql.{PsqlGameEventQueries, PsqlGameStateQueries, PsqlJoinGameRequestQueries}
import org.scalatest.Inside

import java.time.Instant

class JoinGameServiceSpec extends AnyWordSpec with Matchers with IOTest with DBTest with Inside {
  "JoinGameService" should {
    val gameStateStore = new GameStateStore(PsqlGameStateQueries, tr)
    val userStore = new PsqlUserRepository(tr)
    val service = new JoinGameService(PsqlGameStateQueries, PsqlGameEventQueries, PsqlJoinGameRequestQueries, tr)

    "be able to pair two requests and create a game" in {
      val userOneId = UserId.create
      val userOne = User(userOneId, s"${userOneId}@example.com", Instant.now())
      val userTwoId = UserId.create
      val userTwo = User(userTwoId, s"${userTwoId}@example.com", Instant.now())
      val rules = GameRules(3, 3, 3)
      val resultF = for {
        _ <- userStore.insertOrUpdate(userOne)
        _ <- userStore.insertOrUpdate(userTwo)
        _ <- service.saveJoinGameRequest(userOne.id)
        _ <- service.saveJoinGameRequest(userTwo.id)
        games <- service.createGames(rules)
      } yield {
        games
      }

      val result = resultF.unsafeRunSync()

      result should have size(1)
      inside(result(0)) {
        case GameState(gameId, createdAt, playerOne, playerTwo,
          Game(field, expectingMoveFrom, fetchedRules, movesMade)
        ) => {
          List(playerOne, playerTwo) should contain theSameElementsAs List(userOne.id, userTwo.id)
          fetchedRules shouldBe rules
          movesMade shouldBe empty
        }
      }
    }
  }
}
