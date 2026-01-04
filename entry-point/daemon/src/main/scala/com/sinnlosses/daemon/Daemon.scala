package com.sinnlosses.daemon

class Daemon extends com.twitter.inject.app.App {
  override protected def run(): Unit = {
    println("Hello World")
  }
}

// entry point
object DaemonMain extends Daemon
