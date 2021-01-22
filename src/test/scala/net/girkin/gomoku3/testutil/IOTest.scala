package net.girkin.gomoku3.testutil

import cats.effect._

trait IOTest {

  def ioTest[T](block: IO[T]): Unit =
    block.unsafeRunSync()

}
