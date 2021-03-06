package com.evolutiongaming.persistence

import scala.PartialFunction.condOpt

object PersistenceId {
  def apply(persistenceType: String, id: String): String = s"$persistenceType-$id"

  def unapply(persistenceId: String): Option[(String, String)] = condOpt(persistenceId split "-") {
    case Array(persistenceType, id) => persistenceType -> id
  }
}