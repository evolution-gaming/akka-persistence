package com.evolutiongaming.persistence

import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SnapshotMetadata, SnapshotSelectionCriteria}

import scala.util.Failure

trait MeteredSnapshotStore extends SnapshotStore with Metered {
  import context.dispatcher

  def registry = PersistentMetrics.AkkaRegistry
  def metricPrefix = "persistence.snapshotStore."

  abstract override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria) = {
    time("load") {
      val future = super.loadAsync(persistenceId, criteria)
      future onComplete {
        case Failure(e) =>
          context.system.log.error(e, s"Failed to load snapshot persistenceId: $persistenceId criteria: $criteria")
        case _ =>
      }
      future
    }
  }

  abstract override def saveAsync(metadata: SnapshotMetadata, snapshot: Any) = {
    time("save") {
      super.saveAsync(metadata, snapshot)
    }
  }

  abstract override def deleteAsync(metadata: SnapshotMetadata) = {
    time("delete") {
      super.deleteAsync(metadata)
    }
  }

  abstract override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria) = {
    time("deleteMany") {
      super.deleteAsync(persistenceId, criteria)
    }
  }
}