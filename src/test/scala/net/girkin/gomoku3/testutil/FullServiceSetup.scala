package net.girkin.gomoku3.testutil

import cats.effect.{IO, Resource}
import doobie.Transactor
import doobie.free.KleisliInterpreter
import doobie.util.transactor.{Strategy, Transactor}
import net.girkin.gomoku3.GameRules
import net.girkin.gomoku3.auth.{CookieAuth, PrivateKey}
import net.girkin.gomoku3.http.{GameRoutes, GameRoutesService}
import net.girkin.gomoku3.store.{GameEventQueries, GameStateQueries, GameStateStore, JoinGameRequestQueries, JoinGameService}
import org.http4s.implicits.*

trait NoopTransactor {
  val transactor = Transactor(
    (),
    (_: Unit) => Resource.pure(null),
    KleisliInterpreter[IO].ConnectionInterpreter,
    Strategy.void
  )
}

trait AuthSetup {
  val loginPageUri = uri"/auth/login"
  val privateKeyStr = "sadflwqerousadv123"
  val privateKey = PrivateKey(scala.io.Codec.toUTF8(privateKeyStr))
  val auth = new CookieAuth[IO](privateKey, loginPageUri)
}

trait FullSeviceSetup extends AuthSetup with MockitoScalaSugar with NoopTransactor {
  val ticTacToeRules = GameRules(3, 3, 3)
  val gameStateQueries = mock[GameStateQueries]
  val gameEventQueries = mock[GameEventQueries]
  val joinGameRequestQueries = mock[JoinGameRequestQueries]

  val gameStateStore = new GameStateStore(gameStateQueries, transactor)
  val joinGameService = new JoinGameService(gameStateQueries, gameEventQueries, joinGameRequestQueries, transactor)
  val gameService = new GameRoutesService(gameStateStore, joinGameService, ticTacToeRules)
  val gameRoutes = new GameRoutes(auth, gameService)
}

