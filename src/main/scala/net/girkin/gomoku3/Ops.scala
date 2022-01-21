package net.girkin.gomoku3

import cats.implicits.*

object Ops {

  extension[A] (a: A) {
    def |>[B](f: A => B): B = f(a)
  }

}
