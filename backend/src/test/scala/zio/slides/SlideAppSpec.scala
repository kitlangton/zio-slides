package zio.slides

import zio._
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.duration._
import zio.random.Random
import zio.test.Assertion.equalTo
import zio.test.environment.Live
import zio.test._

object SlideAppSpec extends DefaultRunnableSpec {
  def simulateUser: ZIO[Has[SlideApp] with Random with Console, Nothing, TestResult] = for {
    _            <- putStrLn("STARTING")
    delay        <- random.nextIntBetween(0, 3)
    amountToTake <- random.nextIntBetween(2, 5000).delay(delay.seconds).provideSomeLayer[Random](Clock.live)
    taken        <- SlideApp.slideStateStream.take(amountToTake).runCollect.map(_.map(_.slideIndex))
    expected = Chunk.fromIterable(taken.head to taken.last)
  } yield assert(taken)(equalTo(expected))

  def spec: ZSpec[Environment, Failure] =
    suite("SlideAppSpec")(
      testM("subscriptions are interruptible") {
        val total = 1000
        for {
          _   <- SlideApp.receiveAdminCommand(AdminCommand.NextSlide).forever.fork
          _   <- Live.live(ZIO.sleep(1.seconds))
          ref <- Ref.make(0)
          reportFinish = ref.getAndUpdate(_ + 1).flatMap(i => putStrLn(s"FINISHED $i / $total"))
          all <- ZIO.collectAllPar(List.fill(total)(simulateUser <* reportFinish))
        } yield BoolAlgebra.collectAll(all).getOrElse(assertCompletes.negate)
      }
    ).provideCustomLayer(SlideApp.live)
}
