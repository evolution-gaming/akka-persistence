import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import Dependencies._
name := "akka-persistence"

organization := "com.evolutiongaming"

startYear := Some(2017)

organizationName := "Evolution Gaming"

organizationHomepage := Some(url("http://evolutiongaming.com"))

bintrayOrganization := Some("evolutiongaming")

scalaVersion := "2.12.2"

crossScalaVersions := Seq("2.12.2", "2.11.11")

releaseCrossBuild := true

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xfuture")

scalacOptions in (Compile,doc) ++= Seq("-no-link-warnings")

resolvers += Resolver.bintrayRepo("evolutiongaming", "maven")

licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")))

lazy val clusterTests = (Project("akka-persistence", file("."))
  settings (multiJvmSettings: _*)
  settings (fork in Test := true)
  settings (libraryDependencies ++= Akka.All ++ Seq(scalax, scalaTools, metricsScala, evoMetricTools))
  settings (parallelExecution in Test := false)).configs(MultiJvm)

