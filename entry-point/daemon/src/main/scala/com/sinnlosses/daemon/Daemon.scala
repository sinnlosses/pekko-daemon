package com.sinnlosses.daemon

import com.twitter.util.Await
import com.twitter.util.Future
import com.twitter.util.Promise
import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

class Daemon extends com.twitter.inject.app.App {

  override def run(): Unit = {
    implicit val system: ActorSystem = ActorSystem("QuickStart")
    val future = handleSubCommand()

    Await.result(future)
  }

  private def handleSubCommand()(implicit actorSystem: ActorSystem): Future[Unit] = {
    val graph = makeGraph(subCommand())
    runGraph(graph)
  }

  private def subCommand()(implicit
      actorSystem: ActorSystem
  ): Source[Either[Throwable, Unit], UniqueKillSwitch] = {

    def mainFlow(): Flow[NotUsed, Either[Throwable, Unit], NotUsed] = {
      Flow[NotUsed].flatMapConcat { _ =>
        info("処理を開始します。")
        Source.single(Right(()))
      }
    }

    makeDaemonSource(mainFlow())
  }

  private def makeDaemonSource[T, E](mainFlow: Flow[NotUsed, Either[E, T], NotUsed])(implicit
      actorSystem: ActorSystem
  ): Source[Either[E, T], UniqueKillSwitch] = {
    import actorSystem.dispatcher

    Source.fromGraph(
      GraphDSL.createGraph(KillSwitches.single[NotUsed]) {
        implicit builder => killSwitch =>
          import GraphDSL.Implicits._

          val concat = builder.add(Concat[NotUsed]())
          val (queue, source) = Source.queue[NotUsed](1, OverflowStrategy.fail).preMaterialize()

          val ticker = builder.add(
            Flow[NotUsed]
              .throttle(
                elements = 1,
                per = 5.seconds,
                maximumBurst = 1,
                mode = ThrottleMode.Shaping
              )
          )

          val main = builder.add(Flow[NotUsed].flatMapConcat { _ =>
            mainAsSubSource(mainFlow)
              .mapMaterializedValue(_.onComplete {
                case Success(_)         => queue.offer(NotUsed)
                case Failure(exception) => queue.fail(exception)
              })
          })

          // format: off
          Source.single(NotUsed) ~> concat
                          source ~> concat
                                    concat ~> killSwitch ~> ticker ~> main
          // format: on
          SourceShape(main.out)
      }
    )
  }

  private def mainAsSubSource[T, E](mainFlow: Flow[NotUsed, Either[E, T], NotUsed])
      : Graph[SourceShape[Either[E, T]], concurrent.Future[Done]] = {
    // サブソース自身の完了を materialized value として返す
    Source
      .single(NotUsed)
      .via(mainFlow)
      .watchTermination()(Keep.right)
  }

  private def makeGraph[E <: Throwable, T](source: Source[Either[E, T], UniqueKillSwitch])
      : RunnableGraph[(UniqueKillSwitch, concurrent.Future[Done])] = {
    val errorSink = Sink.foreach[Either[E, T]] {
      case Right(_) => // ignore
      case Left(e)  => logger.error(e.getMessage)
    }

    source.toMat(errorSink)(Keep.both)
  }

  private def runGraph(graph: RunnableGraph[(UniqueKillSwitch, concurrent.Future[Done])])(implicit
      actorSystem: ActorSystem
  ): Future[Unit] = {
    import actorSystem.dispatcher

    info("デーモンプロセス開始")
    val (killSwitch, done) = graph.run()

    val promise = Promise[Unit]()
    done.onComplete {
      case Success(_)         => promise.setDone()
      case Failure(exception) => promise.setException(exception)
    }

    promise.setInterruptHandler(_ => killSwitch.shutdown())
    promise
  }

  private def process001(): Unit = {
    implicit val system: ActorSystem = ActorSystem("QuickStart")
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    // Source を queue にし、後から要素を追加
    val source = Source.queue[Int](bufferSize = 16, OverflowStrategy.backpressure)
    val sink = Sink.fold[Int, Int](0) { (acc, x) =>
      val s = acc + x
      println(s"[sink] received=$x, sum=$s")
      s
    }

    val g: RunnableGraph[(SourceQueueWithComplete[Int], concurrent.Future[Int])] =
      RunnableGraph.fromGraph(
        GraphDSL.createGraph(source, sink)((q, f) => (q, f)) {
          implicit builder => (in, out) =>
            import GraphDSL.Implicits._

            val f1 = Flow[Int].map(_ + 10)

        // format: off
        // in ~> f1 ~> bcast
        //             bcast ~> f2 ~> merge
        //             bcast ~> f4 ~> merge
        //                            merge ~> f3 ~> out
          in ~> f1 ~> out
        // format: on

            ClosedShape
        }
      )

    val (queue, resultF) = g.run()

    // 3秒に1回、1を投入
    val tick = system.scheduler.scheduleAtFixedRate(0.seconds, 3.seconds)(() => {
      queue.offer(1); ()
    })

    // デモ用に20秒後に完了して合計を出力
    system.scheduler.scheduleOnce(20.seconds) {
      tick.cancel()
      queue.complete()
    }

    resultF.onComplete { _ =>
      system.terminate()
    }
  }

  private def process002(): Unit = {
    implicit val system: ActorSystem = ActorSystem("QuickStart")
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val (switch: UniqueKillSwitch, done) = Source.tick(0.seconds, 1.second, "tick")
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.foreach(println))(Keep.both)
      .run()

    system.scheduler.scheduleOnce(5.seconds) {
      switch.shutdown() // 正常完了にする（abort(ex) で失敗完了も可）
    }
  }
}

// entry point
object DaemonMain extends Daemon
