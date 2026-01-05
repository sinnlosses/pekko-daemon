import sbt.ThisBuild

lazy val noPublish = publish := {}

lazy val baseSettings = Seq(
  ThisBuild / version := "0.0.1",
  ThisBuild / versionScheme := Some("semver-spec"),
  organization := "com.sinnlosses",
  scalaVersion := Dependencies.versions.scala,
  version := (ThisBuild / version).value
)

lazy val `root` = (project in file("."))
  .settings(baseSettings)
  .settings(name := "sinnlosses")
  .aggregate(
    daemon
  )

lazy val application = (project in file("application"))
  .settings(baseSettings)
  .settings(noPublish)
  .settings(
    libraryDependencies ++= Dependencies.bundle.pekko
  )

lazy val adapter = (project in file("adapter"))
  .settings(baseSettings)
  .settings(noPublish)
  .settings(
    libraryDependencies ++= Dependencies.finagleInjectCore
  )
  .dependsOn(application)

lazy val logging = (project in file("logging"))
  .settings(baseSettings)
  .settings(noPublish)
  .settings(
    libraryDependencies ++= Seq(Dependencies.logback, Dependencies.logstashLogbackEncoder)
  )

lazy val daemon = (project in file("entry-point/daemon"))
  .settings(baseSettings)
  .settings(libraryDependencies ++= Dependencies.finatraApp)
  .dependsOn(adapter % "test->test;compile->compile")
  .dependsOn(logging % "test->test;compile->compile")
