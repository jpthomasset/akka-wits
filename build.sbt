import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val dependencies = Seq(
  "com.typesafe" % "config" % "1.3.1",
  "com.typesafe.akka" %% "akka-actor" % "2.4.16",
  "com.typesafe.akka" %% "akka-remote" % "2.4.16",
  "com.typesafe.akka" %% "akka-cluster" % "2.4.16",
  "com.typesafe.akka" %% "akka-cluster-metrics" % "2.4.16"
)

val testDependencies = Seq(
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.16" % "test",
  "com.typesafe.akka" %% "akka-multi-node-testkit" % "2.4.16" %"test"
)

lazy val commonSettings = Seq(
  organization := "com.frenchcoder",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.8",
  licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  homepage := Some(url("https://github.com/jpthomasset/akka-wits"))
)

lazy val root = (project in file("."))
  .settings(SbtMultiJvm.multiJvmSettings: _*)
  .settings(
    name := "akka-wits",
    description := "Akka cluster service locator",
    commonSettings,
    libraryDependencies ++= dependencies ++ testDependencies,
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
    parallelExecution in Global := false,
    executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
      case (testResults, multiNodeResults)  =>
        val overall =
          if (testResults.overall.id < multiNodeResults.overall.id)
            multiNodeResults.overall
          else
            testResults.overall
        Tests.Output(overall,
          testResults.events ++ multiNodeResults.events,
          testResults.summaries ++ multiNodeResults.summaries)
    }
  )
  .configs (MultiJvm)

lazy val sample = (project in file("sample"))
  .settings(
    name := "akka-wits-sample",
    commonSettings,
    libraryDependencies ++= dependencies,
    publishArtifact := false
  )
  .dependsOn(root)

/* Publishing informations */
pomExtra := (
    <scm>
      <url>https://github.com/jpthomasset/akka-wits</url>
      <connection>scm:git@github.com:jpthomasset/akka-wits.git</connection>
    </scm>
    <developers>
      <developer>
        <id>jpthomasset</id>
        <name>Jean-Pierre Thomasset</name>
        <url>https://github.com/jpthomasset/</url>
      </developer>
    </developers>)

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}
