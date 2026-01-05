import sbt.*

object Dependencies {

  object versions {
    val scala = "2.13.17"

    val finatraFamily = "24.2.0"
    val pekko = "1.1.5"
    val logback = "1.4.7"
    val logstashLogbackEncoder = "7.3"
  }

  object bundle {

    lazy val pekko: Seq[ModuleID] = Seq(logback, pekkoTyped, pekkoStream)
  }

  lazy val finatraApp: Seq[ModuleID] = Seq(
    "com.twitter" %% "inject-app" % versions.finatraFamily
  )

  lazy val pekkoTyped: ModuleID = "org.apache.pekko" %% "pekko-actor-typed" % versions.pekko

  lazy val pekkoStream: ModuleID = "org.apache.pekko" %% "pekko-stream" % versions.pekko

  lazy val logback: ModuleID = "ch.qos.logback" % "logback-classic" % versions.logback

  lazy val logstashLogbackEncoder: ModuleID =
    "net.logstash.logback" % "logstash-logback-encoder" % versions.logstashLogbackEncoder

  lazy val finagleInjectCore: Seq[ModuleID] = Seq(
    "com.twitter" %% "finagle-core" % versions.finatraFamily,
    "com.twitter" %% "inject-core" % versions.finatraFamily
  )
}
