package com.evolutiongaming.persistence

import akka.actor.{DiagnosticActorLogging, ReceiveTimeout, Status}
import akka.persistence._
import com.evolutiongaming.util.ConfigHelper._
import com.evolutiongaming.util.Nel
import com.github.t3hnar.scalax.RichClass
import com.typesafe.config.Config

import scala.collection.immutable.Seq
import scala.compat.Platform
import scala.concurrent.duration._

trait PersistentActor extends akka.persistence.PersistentActor with DiagnosticActorLogging {

  private var savedSnapshotsNumber: Long = 0L
  private var lastSnapshotTimestamp: Long = Platform.currentTime

  def id: String
  def persistenceType: String

  lazy val persistenceId: String = PersistenceId(persistenceType, id)

  private lazy val metrics = PersistentMetrics(this)

  private var started = Platform.currentTime

  private lazy val _persistenceConfig = PersistenceConfig(
    context.system.settings.config getConfig "evolutiongaming",
    persistenceType)

  lazy val logger: PersistentActorLogger = PersistentActorLogger(this, log)

  def persistenceConfig: PersistenceConfig = _persistenceConfig

  override def preStart(): Unit = {
    super.preStart()
    metrics.preStart()
    started = Platform.currentTime
  }

  override def postStop(): Unit = {
    super.postStop()
    metrics.postStop()
  }

  def rcvDefault: Receive = PartialFunction.empty

  def rcvCommand: Receive

  def rcvRecover: Receive

  def receiveCommand = Log("receiveCommand", rcvCommand orElse rcvDefault orElse rcvUnhandled)

  def receiveRecover = Recovery(Log("receiveRecover", rcvRecover orElse rcvUnhandled))

  def rcvUnhandled: Receive = {
    case _: Status.Failure         =>
    case _: SnapshotOffer          =>
    case _: SaveSnapshotFailure    =>
    case x: SaveSnapshotSuccess    => onSaveSnapshotSuccess(x)
    case _: DeleteSnapshotSuccess  =>
    case _: DeleteSnapshotsSuccess =>
    case _: DeleteSnapshotFailure  =>
    case _: DeleteSnapshotsFailure =>
    case RecoveryCompleted         =>
  }

  override def saveSnapshot(snapshot: Any) = {
    lastSnapshotTimestamp = Platform.currentTime
    super.saveSnapshot(snapshot)
  }

  def onSaveSnapshotSuccess(msg: SaveSnapshotSuccess): Unit = {
    val toSequenceNr = msg.metadata.sequenceNr - 1

    if (persistenceConfig.deletePrevSnapshotsOncePer > 0 || persistenceConfig.deletePrevEventsOncePer > 0) {
      savedSnapshotsNumber = savedSnapshotsNumber + 1
    }

    if (persistenceConfig.deletePrevEventsOncePer > 0 && savedSnapshotsNumber >= persistenceConfig.deletePrevEventsOncePer) {
      deleteMessages(toSequenceNr)
    }

    if (persistenceConfig.deletePrevSnapshotsOncePer > 0 && savedSnapshotsNumber >= persistenceConfig.deletePrevSnapshotsOncePer) {
      deleteSnapshots(SnapshotSelectionCriteria(maxSequenceNr = toSequenceNr))

      savedSnapshotsNumber = 0
    }
  }

  override def toString: String = persistenceId

  case class Recovery(rcv: Receive) extends Receive {
    private var events = 0l

    def isDefinedAt(x: Any) = rcv isDefinedAt x

    def apply(x: Any) = {
      rcv apply x

      x match {
        case SnapshotOffer     =>
        case RecoveryCompleted =>
          val duration = Platform.currentTime - started
          metrics.onRecoveryCompleted(events, duration)
          if (duration >= persistenceConfig.recoveryWarnThreshold) {
            logger.warn(s"completed recovery of $events events in ${ duration / 1000 }s")
          } else {
            logger.info(s"completed recovery of $events events in ${ duration }ms")
          }

        case _                 => events += 1
      }
    }
  }

  case class Log(name: String, rcv: Receive) extends Receive {
    def isDefinedAt(x: Any) = rcv isDefinedAt x

    def apply(x: Any) = {
      x match {
        case x: SaveSnapshotSuccess    => logger.info(x)
        case x: SaveSnapshotFailure    => logger.error(x, x.cause)
        case x: DeleteSnapshotSuccess  => logger.debug(x)
        case x: DeleteSnapshotsSuccess => logger.debug(x)
        case x: DeleteSnapshotFailure  => logger.warn(x)
        case x: DeleteSnapshotsFailure => logger.warn(x)
        case x: Status.Failure         => logger.warn(x)
        case ReceiveTimeout            => logger.info(s"$x, no activity for ${context.receiveTimeout}")
        case _                         => logger.debug(x)
      }
      rcv apply x
      for (commandType <- msgType(x)) metrics.onRcv(name, commandType)
    }
  }

  override def persist[A](event: A)(handler: (A) => Unit) = {
    super.persist(event)(metrics.handler(handler))
  }

  override def persistAsync[A](event: A)(handler: (A) => Unit) = {
    super.persistAsync(event)(metrics.handler(handler))
  }

  override def persistAll[A](events: Seq[A])(handler: (A) => Unit) = {
    super.persistAll(events)(metrics.handler(handler))
  }

  override def persistAllAsync[A](events: Seq[A])(handler: (A) => Unit) = {
    super.persistAllAsync(events)(metrics.handler(handler))
  }

  protected def meterCmd[T](f: (() => Unit) => T): T = {
    val startTime = Platform.currentTime
    f(() => metrics.onCmd(startTime))
  }

  protected def msgType(x: Any): Nel[String] = Nel(x.getClass.simpleName)

  def shouldSaveSnapshot: Boolean = {

    def timestampCheck = {
      lastSnapshotTimestamp + persistenceConfig.saveSnapshotNotOftenThan.toMillis <= Platform.currentTime
    }

    def seqNrCheck = lastSequenceNr % persistenceConfig.saveSnapshotOncePer == 0

    seqNrCheck && timestampCheck
  }
}

case class PersistenceConfig(
  deletePrevSnapshotsOncePer: Int = 1,
  deletePrevEventsOncePer: Int = 0,
  saveSnapshotOncePer: Int = 500,
  saveSnapshotNotOftenThan: FiniteDuration = 1.minute,
  recoveryWarnThreshold: Long = 5.seconds.toMillis,
  receiveTimeout: FiniteDuration = 1.hour)

object PersistenceConfig {

  def apply(config: Config, persistenceType: String): PersistenceConfig = {
    def get[T: FromConf](path: String) = config.getOrElse(s"$persistenceType.$path", s"persistence.$path")

    PersistenceConfig(
      deletePrevSnapshotsOncePer = get("delete-prev-snapshots-once-per"), // delete-prev-snapshot, 0 - is never
      deletePrevEventsOncePer = get("delete-prev-events-once-per"), // delete-prev-events, 0 - is never
      saveSnapshotOncePer = get("save-snapshot-once-per"),
      saveSnapshotNotOftenThan = get("save-snapshot-not-often-than"),
      recoveryWarnThreshold = get[FiniteDuration]("recovery-warn-threshold").toMillis,
      receiveTimeout = get("receive-timeout"))
  }
}