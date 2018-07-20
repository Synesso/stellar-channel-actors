import sbt.Keys.organization
import scoverage.ScoverageKeys.coverageMinimum

// format: off
lazy val commonSettings = Seq(
  name := "stellar-channel",
  organization := "jem",
  scalaVersion := "2.12.6",
  scalacOptions ++= Seq(
    "-Yrangepos",
    "-unchecked",
    "-deprecation",
    "-feature",
    "-language:postfixOps",
    "-encoding",
    "UTF-8",
    "-target:jvm-1.8"),
  fork := true,
  coverageMinimum := 95,
  coverageFailOnMinimum := true,
  licenses += ("Apache-2.0", url("https://opensource.org/licenses/Apache-2.0"))
)

resolvers += "scala-stellar-sdk-repo" at "https://dl.bintray.com/synesso/mvn"

enablePlugins(GitVersioning)

lazy val root = (project in file("."))
  .settings(
    commonSettings,
    libraryDependencies ++= List(
      "stellar.scala.sdk" %% "scala-stellar-sdk" % "0.1.4",
      "com.typesafe.akka" %% "akka-actor" % "2.5.13",
      "com.typesafe.akka" %% "akka-stream" % "2.5.13",
      "org.specs2" %% "specs2-core" % "4.0.0" % "test",
      "org.specs2" %% "specs2-scalacheck" % "4.0.0" % "test"
    )
  )
