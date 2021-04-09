package zio.slides

import zhttp.http._
import zhttp.service._
import zhttp.socket._
import zio._
import zio.console.{Console, putStrErr}
import zio.json.{DecoderOps, EncoderOps}
import zio.slides.ServerCommand.{SendPopulationStats, SendQuestionState, SendSlideState, SendUserId, SendVotes}
import zio.slides.VoteState.UserId
import zio.stream.ZStream

object Main extends App {
  def socket(userId: UserId): Socket[Console with Has[SlideApp], SocketError, WebSocketFrame, WebSocketFrame] =
    Socket.collect[WebSocketFrame] { case WebSocketFrame.Text(text) =>
      text.fromJson[ClientCommand] match {
        case Left(error) =>
          ZStream.fromEffect(putStrErr(s"DECODING ERROR $error")).as(WebSocketFrame.ping)
        case Right(UserCommand.ConnectionPlease()) =>
          ZStream.mergeAllUnbounded()(
            SlideApp.slideStateStream.map[ServerCommand](SendSlideState).map(s => WebSocketFrame.text(s.toJson)),
            SlideApp.questionStateStream.map[ServerCommand](SendQuestionState).map(s => WebSocketFrame.text(s.toJson)),
            SlideApp.voteStream.map[ServerCommand](SendVotes).map(s => WebSocketFrame.text(s.toJson)),
            SlideApp.populationStatsStream
              .map[ServerCommand](SendPopulationStats)
              .map(s => WebSocketFrame.text(s.toJson)),
            ZStream.succeed[ServerCommand](SendUserId(userId)).map(s => WebSocketFrame.text(s.toJson))
          )
        case Right(command) =>
          ZStream.fromEffect(SlideApp.receive(userId, command)).as(WebSocketFrame.ping)
      }
    }

  private val app =
    Http.collect { case Method.GET -> Root / "ws" =>
      val userId = UserId.random
      Response.socket(socket(userId))
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    Server
      .start(8088, app)
      .provideCustomLayer(SlideApp.live)
      .exitCode
  }
}
