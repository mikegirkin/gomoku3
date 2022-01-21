package net.girkin.gomoku3

import org.slf4j.LoggerFactory
import cats.effect.IO

trait Logging {
  val logger = LoggerFactory.getLogger(this.getClass.toString)
}

trait FunctionalLogging {
  private[this] val logger = LoggerFactory.getLogger(this.getClass.toString)

  private def logIO(fn: String => Unit)(data: String): IO[Unit] = {
    IO.delay {
      fn(data)
    }
  }

  def trace(msg: String): IO[Unit] = {
    logIO(logger.trace)(msg)
  }

  def debug(msg: String): IO[Unit] = {
    logIO(logger.debug)(msg)
  }

  def info(msg: String): IO[Unit] = {
    logIO(logger.info)(msg)
  }

  def warn(msg: String): IO[Unit] = {
    logIO(logger.warn)(msg)
  }

  def error(msg: String): IO[Unit] = {
    logIO(logger.error)(msg)
  }

}
