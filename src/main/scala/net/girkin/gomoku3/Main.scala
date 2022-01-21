package net.girkin.gomoku3

import cats.effect.{ExitCode, IO}
import cats.effect.unsafe.IORuntime
import cats.effect.implicits.*
import cats.syntax.*
import doobie.util.transactor.Transactor
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.blaze.server.*
import org.http4s.implicits.*
import fs2.Pipe
import net.girkin.gomoku3.auth.{AuthPrimitives, GoogleAuthImpl, PrivateKey, PsqlUserRepository, SecurityConfiguration}
import org.http4s.HttpRoutes
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.server.Router

import java.nio.channels.NetworkChannel
import scala.concurrent.ExecutionContext


@main  def hello: Unit = {
  given runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  val helloWorldService = HttpRoutes.of[IO] {
    case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name.")
  }

  val db = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql://localhost:5432/gomoku3"
  )
  val userRepository = new PsqlUserRepository(db)

  val privateKeyStr = "sadflwqerousadv123"
  val privateKey = PrivateKey(scala.io.Codec.toUTF8(privateKeyStr))

  val securityConfiguration = SecurityConfiguration(
    "270746747187-0ri8ig249up93ranj0l9qvpkhufaocv7.apps.googleusercontent.com",
    "WluSEQw9iNB2iIabeUDOf-no"
  )
  val client = BlazeClientBuilder[IO].resource

  val authService = new GoogleAuthImpl[IO](
    new AuthPrimitives[IO](privateKey),
    userRepository,
    securityConfiguration,
    client
  )

  val router = Router(
    "/auth" -> authService.service,
    "/" -> helloWorldService,
  ).orNotFound

  BlazeServerBuilder[IO]
    .bindHttp(ServerConfiguration.port, ServerConfiguration.host)
    .withHttpApp(router)
    .serve
    .compile
    .drain
    .as(ExitCode.Success)
    .unsafeRunSync()
}


