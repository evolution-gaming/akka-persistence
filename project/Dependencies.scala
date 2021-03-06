import sbt._

object Dependencies {
  object Akka {
    private val akkaVersion = "2.5.4"

    val Actor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
    val Cluster = "com.typesafe.akka" %% "akka-cluster" % akkaVersion % Compile
    val ClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion % Compile

    val All = Seq(Actor, Cluster, ClusterSharding)
  }

  private val metricsScalaVersion = "3.5.9"

  val scalax = "com.github.t3hnar" %% "scalax" % "3.2" % Compile
  val scalaTools = "com.evolutiongaming" %% "scala-tools" % "1.12"
  val metricsScala = "nl.grons" %% "metrics-scala" % metricsScalaVersion % Compile
  val evoMetricTools = "com.evolutiongaming" %% "metric-tools" % "0.8"
}
