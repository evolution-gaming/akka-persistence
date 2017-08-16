package com.evolutiongaming.persistence

import akka.event.LoggingAdapter

trait PersistentActorLogger {

  def debug(msg: => Any): Unit

  def info(msg: => Any): Unit

  def warn(msg: => Any): Unit

  def error(msg: => Any): Unit

  def error(msg: => Any, cause: Throwable): Unit
}

object PersistentActorLogger {

  lazy val Empty: PersistentActorLogger = new PersistentActorLogger {
    def debug(msg: => Any): Unit = ()
    def info(msg: => Any): Unit = ()
    def warn(msg: => Any): Unit = ()
    def error(msg: => Any): Unit = ()
    def error(msg: => Any, cause: Throwable): Unit = ()
  }

  def apply(
    adapter: LoggingAdapter,
    persistenceId: String,
    lastSequenceNr: => Long): PersistentActorLogger = {

    new Impl(adapter, persistenceId, lastSequenceNr)
  }

  def apply(actor: PersistentActor, log: LoggingAdapter): PersistentActorLogger = {
    apply(log, actor.persistenceId, actor.lastSequenceNr)
  }

  private class Impl(
    adapter: LoggingAdapter,
    persistenceId: String,
    lastSequenceNr: => Long) extends PersistentActorLogger {

    private def pattern = "{}({}): {}"

    def debug(msg: => Any): Unit = {
      adapter.debug(pattern, persistenceId, lastSequenceNr, msg)
    }

    def info(msg: => Any): Unit = {
      adapter.info(pattern, persistenceId, lastSequenceNr, msg)
    }

    def warn(msg: => Any): Unit = {
      adapter.warning(pattern, persistenceId, lastSequenceNr, msg)
    }

    def error(msg: => Any): Unit = {
      adapter.error(pattern, persistenceId, lastSequenceNr, msg)
    }

    def error(msg: => Any, cause: Throwable): Unit = {
      adapter.error(cause, pattern, persistenceId, lastSequenceNr, msg)
    }
  }
}