package com.evolutiongaming.persistence

import akka.actor.{ActorRef, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion

trait PersistentShardActor extends PersistentActor {
  import PersistentShardActor._

  lazy val id: String = self.path.name
  lazy val persistenceType: String = shardRegion.path.parent.name

  override def preStart(): Unit = {
    super.preStart()
    context setReceiveTimeout persistenceConfig.receiveTimeout
  }

  override def rcvDefault: Receive = {
    case Passivated     => onPassivated()
    case ReceiveTimeout => passivate(Some(ReceiveTimeout))
    case Stop           =>
      log.warning("{}({}) stop", persistenceId, lastSequenceNr)
      passivate(Some(Stop))
  }

  def shardRegion: ActorRef = context.parent

  def passivate(reason: Option[Any] = None): Unit = {
    if (recoveryFinished) {
      log.debug("{}({}) passivate", persistenceId, lastSequenceNr)
      shardRegion ! ShardRegion.Passivate(Passivated)
    }
  }

  def onPassivated(): Unit = {
    log.debug("{}({}) passivated", persistenceId, lastSequenceNr)
    context stop self
  }
}

object PersistentShardActor {
  case object Passivated
  case object Stop
}