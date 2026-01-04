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

lazy val daemon = (project in file("entry-point/daemon"))
  .settings(baseSettings)
  .settings(libraryDependencies ++= Dependencies.finatraApp)
