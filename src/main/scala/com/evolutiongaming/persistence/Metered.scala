package com.evolutiongaming.persistence

import com.codahale.metrics.{Meter, MetricRegistry}
import com.evolutiongaming.util.MetricHelper._

import scala.concurrent.{ExecutionContext, Future}

trait Metered {
  def registry: MetricRegistry

  def metricPrefix: String


  def time[T](name: String)(f: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val timer = registry.timer(metricPrefix + name)
    timer.timeFuture(f)
  }

  def meter(name: String): Meter = registry.meter(metricPrefix + name)
}