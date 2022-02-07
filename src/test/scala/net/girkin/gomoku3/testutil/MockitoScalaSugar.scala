package net.girkin.gomoku3.testutil

import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers.argThat

import scala.reflect.ClassTag

trait MockitoScalaSugar {
  protected def mock[T <: AnyRef : ClassTag] = {
    org.mockito.Mockito.mock(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])
  }

  protected def argWhere[T](predicate: PartialFunction[T, Boolean]) = argThat {
    new ArgumentMatcher[T] {
      override def matches(argument: T): Boolean = {
        predicate.lift.apply(argument).getOrElse(false)
      }
    }
  }
}
