package zio.slides

import zio._
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.duration._
import zio.random.Random
import zio.slides.VoteState.UserId
import zio.test.Assertion.{forall, isTrue}
import zio.test.environment.{Live, TestConsole}
import zio.test.{DefaultRunnableSpec, TestAspect, ZSpec, assert}

object SlideAppSpec extends DefaultRunnableSpec {
  def simulateUser: ZIO[Has[SlideApp] with Random with Console, Nothing, (Int, Int)] = for {
    _            <- putStrLn("STARTING")
    delay        <- random.nextIntBetween(0, 3)
    amountToTake <- random.nextIntBetween(1, 5000).delay(delay.seconds).provideSomeLayer[Random](Clock.live)
    taken        <- SlideApp.slideStateStream.take(amountToTake).runCollect.map(_.length)
  } yield (amountToTake, taken)

  def spec: ZSpec[Environment, Failure] =
    suite("SlideAppSpec")(
      testM("subscriptions are interruptible") {
        val total = 1000
        for {
          _   <- SlideApp.receiveAdminCommand(AdminCommand.NextStep).forever.fork
          _   <- Live.live(ZIO.sleep(1.seconds))
          ref <- Ref.make(0)
          reportFinish = ref.getAndUpdate(_ + 1).flatMap(i => putStrLn(s"FINISHED $i / $total"))
          all <- ZIO.collectAllPar(List.fill(total)(simulateUser <* reportFinish))
        } yield assert(all.map { case (i, i1) => i == i1 })(forall(isTrue))
      }
    ).provideCustomLayer(SlideApp.live) // @@ TestAspect.silent
}
