package com.sinnlosses.daemon

import com.twitter.util.{Await, Future => TwitterFuture, Promise}
import org.apache.pekko.{Done, NotUsed}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl._

import scala.concurrent.{Future => ScalaFuture}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class Daemon extends com.twitter.inject.app.App {

  override def run(): Unit = {
    implicit val system: ActorSystem = ActorSystem("Daemon")
    val future = handleSubCommand()

    Await.result(future)
  }

  private def handleSubCommand()(implicit actorSystem: ActorSystem): TwitterFuture[Unit] = {
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

          val ticker = builder.add(Flow[NotUsed].throttle(
            elements = 1,
            per = 5.seconds,
            maximumBurst = 1,
            mode = ThrottleMode.Shaping
          ))

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
      : Graph[SourceShape[Either[E, T]], ScalaFuture[Done]] = {
    // サブソース自身の完了を materialized value として返す
    Source
      .single(NotUsed)
      .via(mainFlow)
      .completionTimeout(5.seconds)
      .watchTermination()(Keep.right)
  }

  private def makeGraph[E <: Throwable, T](source: Source[Either[E, T], UniqueKillSwitch])
      : RunnableGraph[(UniqueKillSwitch, ScalaFuture[Done])] = {
    val errorSink = Sink.foreach[Either[E, T]] {
      case Right(_) => // ignore
      case Left(e)  => logger.error(e.getMessage)
    }

    source.toMat(errorSink)(Keep.both)
  }

  private def runGraph(graph: RunnableGraph[(UniqueKillSwitch, ScalaFuture[Done])])(implicit
      actorSystem: ActorSystem
  ): TwitterFuture[Unit] = {
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
}

// entry point
object DaemonMain extends Daemon
