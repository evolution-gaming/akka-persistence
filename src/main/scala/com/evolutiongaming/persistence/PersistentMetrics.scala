package com.evolutiongaming.persistence

import akka.event.LoggingAdapter
import com.codahale.metrics.MetricRegistry
import com.evolutiongaming.util.MetricHelper
import nl.grons.metrics.scala.{MetricBuilder, MetricName}

import scala.compat.Platform

class PersistentMetrics(id: String, persistenceType: String, log: LoggingAdapter, metric: MetricBuilder) {

  private val counter = metric.counter(s"$persistenceType.actors")
  private val persistTime = metric.histogram("persist")
  private val receiveCommandTimeAll = metric.histogram(s"receiveCommand.time")
  private val receiveCommandTime = metric.histogram(s"$persistenceType.receiveCommand.time")
  private val recoveryTime = metric.histogram("recovery")


  def preStart(): Unit = {
    counter.inc()
  }

  def postStop(): Unit = {
    counter.dec()
  }

  def onRcv(rcvName: String, commandType: String): Unit = {
    metric.meter(s"$persistenceType.$rcvName").mark()
    metric.meter(s"$persistenceType.$rcvName.$commandType").mark()
  }

  def onCmd(start: Long, now: Long = Platform.currentTime): Unit = {
    val duration = now - start
    receiveCommandTime += duration
    receiveCommandTimeAll += duration
  }

  def onRecoveryCompleted(events: Long, duration: Long): Unit = {
    recoveryTime += duration
  }

  def handler[T](handler: T => Unit): (T => Unit) = {
    val start = Platform.currentTime
    var toMeter = true
    event => {
      if (toMeter) {
        val duration = Platform.currentTime - start
        persistTime += duration
        toMeter = false
      }
      handler(event)
    }
  }
}

object PersistentMetrics {

  lazy val AkkaRegistry: MetricRegistry = MetricHelper.exportRegistry("akka", new MetricRegistry)

  lazy val Metrics: MetricBuilder = new MetricBuilder(MetricName("persistence"), AkkaRegistry)

  def apply(actor: PersistentActor): PersistentMetrics = {
    new PersistentMetrics(actor.id, actor.persistenceType, actor.log, Metrics)
  }
}