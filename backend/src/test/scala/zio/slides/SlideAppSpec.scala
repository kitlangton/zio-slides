package zio.slides

import zio._
import zio.test._

object SlideAppSpec extends ZIOSpecDefault {
  def simulateUser: ZIO[SlideApp with Live, Nothing, TestResult] = Live.live(for {
    _                    <- Console.printLine("STARTING").orDie
    delay                <- Random.nextIntBetween(0, 3)
    amountToTake         <- Random.nextIntBetween(2, 5000).delay(delay.seconds)
    receivedSlideIndices <- SlideApp.slideStateStream.take(amountToTake).runCollect.map(_.map(_.slideIndex))
    expected = Chunk.fromIterable(receivedSlideIndices.min to receivedSlideIndices.max)
  } yield assertTrue(receivedSlideIndices == expected))

  def spec =
    suite("SlideAppSpec")(
      test("subscriptions are interruptible") {
        val total = 100
        for {
          _   <- SlideApp.receiveAdminCommand(AdminCommand.NextSlide).forever.fork
          _   <- Live.live(ZIO.sleep(1.seconds))
          ref <- Ref.make(0)
          reportFinish = ref.getAndUpdate(_ + 1).flatMap(i => Console.printLine(s"FINISHED ${i + 1} / $total"))
          all <- ZIO.collectAllPar(List.fill(total)(simulateUser <* reportFinish))
        } yield BoolAlgebra.collectAll(all).getOrElse(assertCompletes.negate)
      }
    ).provideCustomLayer(SlideApp.live)
}
