package com.evolutiongaming.persistence

import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.immutable.Seq
import scala.compat.Platform
import scala.concurrent.Future

trait MeteredJournal extends AsyncWriteJournal with Metered with LazyLogging {
  import context.dispatcher

  def registry = PersistentMetrics.AkkaRegistry
  def metricPrefix = "persistence.journal."


  abstract override def asyncWriteMessages(messages: Seq[AtomicWrite]) = {

    def data = messages map { message =>
      val persistenceId = message.persistenceId
      val payloads = message.payload map { x => x.payload } mkString ","
      s"$persistenceId: $payloads"
    } mkString ";"

    val count = messages.map(_.size).sum
    meter("write.messages").mark(count.toLong)

    time("write") {
      logIfExceeds(1000, duration => s"asyncWriteMessages took $duration ms for $data") {
        super.asyncWriteMessages(messages)
      }
    }
  }

  abstract override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long) = {
    time("delete") {
      super.asyncDeleteMessagesTo(persistenceId, toSequenceNr)
    }
  }

  abstract override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)
    (recoveryCallback: (PersistentRepr) => Unit) = {

    val meter = this.meter("replay.messages")
    time("replay") {
      super.asyncReplayMessages(persistenceId, fromSequenceNr, toSequenceNr, max) { x =>
        meter.mark()
        recoveryCallback(x)
      }
    }
  }

  abstract override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long) = {
    time("highestSequenceNr") {
      super.asyncReadHighestSequenceNr(persistenceId, fromSequenceNr)
    }
  }

  private def logIfExceeds[T](millis: Long, msg: Long => String)(f: => Future[T]): Future[T] = {
    val start = Platform.currentTime
    f andThen { case _ =>
      val duration = Platform.currentTime - start
      if (duration >= millis) logger.warn(msg(duration))
    }
  }
}