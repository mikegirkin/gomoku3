package net.girkin.gomoku3.store

import cats.effect.IO
import cats.effect.concurrent.Ref

class InmemGameStateStore private (storeRef: Ref[IO, Vector[GameState]]) extends GameStateStore {
  
  override def save(state: GameState): IO[Unit] = {
    storeRef.modify {
      store => (store.appended(state), ())
    }
  }
  
  override def getLatest(gameId: GameId): IO[Option[GameState]] = {
    for {
      store <- storeRef.get
    } yield {
      store.findLast(_.gameId == gameId)
    }
  }
}

object InmemGameStateStore {
  def create(): IO[InmemGameStateStore] = {
    for {
      storeRef <- Ref.of[IO, Vector[GameState]](Vector.empty)
    } yield {
      new InmemGameStateStore(storeRef)
    }
  }
}