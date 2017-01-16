val dependencies = Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.16",
  "com.typesafe.akka" %% "akka-cluster" % "2.4.16"

)

val testDependencies = Seq(
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.16" % "test"
)

lazy val commonSettings = Seq(
  organization := "com.frenchcoder",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.8"
)

lazy val root = (project in file("."))
  .settings(
    name := "akka-wits",
    commonSettings,
    libraryDependencies ++= dependencies ++ testDependencies
  )



