package net.girkin.gomoku3

import cats.effect.{ExitCode, IO}
import cats.effect.unsafe.IORuntime
import cats.syntax.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.blaze.server.*
import org.http4s.implicits.*
import fs2.Pipe
import org.http4s.HttpRoutes
import org.http4s.server.Router

import java.nio.channels.NetworkChannel


@main  def hello: Unit = {
  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  val helloWorldService = HttpRoutes.of[IO] {
    case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name.")
  }

  val router = Router(
    "/" -> helloWorldService
  ).orNotFound

  BlazeServerBuilder[IO]
    .bindHttp(8080, "localhost")
    .withHttpApp(router)
    .serve
    .compile
    .drain
    .as(ExitCode.Success)
    .unsafeRunSync()
}


