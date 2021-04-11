package zio.slides

import zhttp.http._
import zhttp.service._
import zhttp.socket._
import zio._
import zio.clock.Clock
import zio.console.{Console, putStrErr, putStrLn}
import zio.json.{DecoderOps, EncoderOps}
import zio.slides.ServerCommand.{SendPopulationStats, SendQuestionState, SendSlideState, SendUserId, SendVotes}
import zio.slides.VoteState.UserId
import zio.stream.ZStream

object Main extends App {
  def userSocket: Socket[Has[SlideApp] with Console, Nothing] = {
    val userId = UserId.random

    val handleOpen = Socket.open { _ =>
      ZStream.fromEffect(putStrLn(s"RECEIVED NEW CONNECTION: \n\t$userId")) *>
        ZStream
          .mergeAllUnbounded()(
            ZStream.fromEffect(SlideApp.userJoined).drain,
            SlideApp.slideStateStream.map(SendSlideState),
            SlideApp.questionStateStream.map(SendQuestionState),
            SlideApp.voteStream.map(SendVotes),
            SlideApp.populationStatsStream.map(SendPopulationStats),
            ZStream.succeed[ServerCommand](SendUserId(userId))
          )
          .map(s => WebSocketFrame.text(s.toJson))
    }

    val handleClose = Socket.close { _ => SlideApp.userLeft }

    val handleCommand = Socket.collect { case WebSocketFrame.Text(text) =>
      text.fromJson[ClientCommand] match {
        case Left(error) =>
          ZStream.fromEffect(putStrErr(s"DECODING ERROR $error")).drain
        case Right(command) =>
          println(s"RECEIVED COMMAND: \n\t$userId \n\t$command")
          ZStream.fromEffect(SlideApp.receive(userId, command)).drain
      }
    }

    handleCommand <+> handleOpen <+> handleClose
  }

  private val app: Http[Console with Has[SlideApp], Throwable] =
    Http.collect { case Method.GET -> Root / "ws" =>
      Response.socket(userSocket)
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = (for {
    port <- system.envOrElse("PORT", "8088").map(_.toInt).orElseSucceed(8088)
    _    <- putStrLn(s"STARTING SERVER ON PORT $port")
    _    <- Server.start(port, app)
  } yield ())
    .provideCustomLayer(SlideApp.live)
    .exitCode
}
