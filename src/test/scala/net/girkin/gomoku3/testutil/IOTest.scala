package net.girkin.gomoku3.testutil

import cats.effect.*
import cats.effect.unsafe.IORuntime

trait IOTest {

  given runtime: IORuntime = cats.effect.unsafe.implicits.global

  def ioTest[T](block: IO[T]): Unit =
    block.unsafeRunSync()

}
