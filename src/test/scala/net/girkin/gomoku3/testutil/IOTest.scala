package net.girkin.gomoku3.testutil

import cats.effect.*
import cats.effect.unsafe.IORuntime
import doobie.util.transactor.Transactor

trait IOTest {

  given runtime: IORuntime = cats.effect.unsafe.implicits.global

  def ioTest[T](block: IO[T]): Unit =
    block.unsafeRunSync()

}

trait DBTest {
  val tr = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql://localhost:5432/gomoku3"
  )
}
