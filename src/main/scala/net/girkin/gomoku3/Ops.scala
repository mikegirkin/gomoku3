package net.girkin.gomoku3

import cats.Functor
import cats.data.EitherT
import cats.implicits.*

object Ops {

  extension[A] (a: A) {
    def |>[B](f: A => B): B = f(a)
  }

}

object EitherTOps {

  extension[F[_], L, R] (a: EitherT[F, L, R])(using Functor[F]) {
    def widenLeft[WL >: L]: EitherT[F, WL, R] = a.leftMap(identity)
  }
}