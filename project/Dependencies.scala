import sbt.*

object Dependencies {

  object versions {
    val scala = "2.13.17"

    val finatraFamily = "24.2.0"
  }

  object bundle {}

  lazy val finatraApp: Seq[ModuleID] = Seq(
    "com.twitter" %% "inject-app" % versions.finatraFamily
  )
}
