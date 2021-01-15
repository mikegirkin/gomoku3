package net.girkin.gomoku3

import cats.effect.IO
import fs2.Pipe

import java.nio.channels.NetworkChannel


@main  def hello: Unit = {
    println("Hello world!")
    println(msg)
  }

  def msg = "I was compiled by Scala 3. :)"


  def simplePipe: Pipe[IO, String, String] = {
    ???
  }






