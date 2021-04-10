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
import zio.duration._

object Main extends App {
  def socket(
      userId: UserId
  ): Socket[Console with Clock with Has[SlideApp], SocketError, WebSocketFrame, WebSocketFrame] =
    Socket.forall[WebSocketFrame] {
      case WebSocketFrame.Text(text) =>
        text.fromJson[ClientCommand] match {
          case Left(error) =>
            ZStream.fromEffect(putStrErr(s"DECODING ERROR $error")).as(WebSocketFrame.text("OH NO"))
          case Right(UserCommand.ConnectionPlease()) =>
            println(s"RECEIVED CONNECTION PLEASE: $userId")
            ZStream
              .mergeAllUnbounded()(
                SlideApp.slideStateStream.map[ServerCommand](SendSlideState).map(s => WebSocketFrame.text(s.toJson)),
                SlideApp.questionStateStream
                  .map[ServerCommand](SendQuestionState)
                  .map(s => WebSocketFrame.text(s.toJson)),
                SlideApp.voteStream.map[ServerCommand](SendVotes).map(s => WebSocketFrame.text(s.toJson)),
                SlideApp.populationStatsStream
                  .map[ServerCommand](SendPopulationStats)
                  .map(s => WebSocketFrame.text(s.toJson)),
                ZStream.succeed[ServerCommand](SendUserId(userId)).map(s => WebSocketFrame.text(s.toJson)),
                ZStream.fromSchedule(Schedule.spaced(20.seconds)).as(WebSocketFrame.ping)
              )
              .mapErrorCause[Nothing] { cause =>
                println(s"CAUSE! $cause")
                cause
              }
          case Right(command) =>
            println(s"RECEIVED COMMEND: \n\t$userId \n\t$command")
            ZStream.fromEffect(SlideApp.receive(userId, command)).as(WebSocketFrame.ping)
        }
      case WebSocketFrame.Close(status, reason) =>
        println(s"RECEIVED CLOSE: \n\t$userId\n\t$status\n\t$reason")
        ZStream.succeed(WebSocketFrame.close(status, reason))
      case WebSocketFrame.Ping =>
        println(s"RECEIVED PING: \n\t$userId")
        ZStream.succeed(WebSocketFrame.pong)
      case other =>
        println(s"RECEIVED OTHER: \n\t$userId\n\t$other")
        ZStream.succeed(WebSocketFrame.text("OH NO"))
    }

  private val app =
    Http.collectM { case Method.GET -> Root / "ws" =>
      for {
        userId <- UIO(UserId.random)
      } yield Response.socket(socket(userId))
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = (for {
    port <- system.envOrElse("PORT", "8088").map(_.toInt).orElseSucceed(8088)
    _    <- putStrLn(s"STARTING SERVER ON PORT $port")
    _    <- Server.start(port, app).provideCustomLayer(SlideApp.live)
  } yield ()).exitCode
}
