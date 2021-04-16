package zio.slides

import zhttp.http._
import zhttp.service._
import zhttp.socket._
import zio._
import zio.console._
import zio.slides.ServerCommand._
import zio.slides.VoteState.UserId
import zio.stream.ZStream
import boopickle.Default._
import io.netty.buffer.Unpooled
import zhttp.core.ByteBuf

import java.nio.ByteBuffer
import scala.util.{Failure, Success, Try}

object SlideAppServer extends App {

  def adminSocket: Socket[Has[SlideApp] with Console, Nothing] =
    pickleSocket { (command: AdminCommand) =>
      ZStream.fromEffect(SlideApp.receiveAdminCommand(command)).drain
    }

  def userSocket: Socket[Has[SlideApp] with Console, Nothing] = {
    val userId = UserId.random

    val handleOpen =
      Socket.open { _ =>
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
            .map { s =>
              val bytes: ByteBuffer = Pickle.intoBytes(s)
              println(s"piclked $s into $bytes")
              val byteBuf = Unpooled.wrappedBuffer(bytes)
              WebSocketFrame.binary(ByteBuf(byteBuf))
            }
      }

    val handleClose = Socket.close { _ => SlideApp.userLeft }

    val handleCommand = pickleSocket { (command: UserCommand) =>
      ZStream.fromEffect(SlideApp.receiveUserCommand(userId, command)).drain
    }

    handleCommand <+> handleOpen <+> handleClose
  }

  private def app(adminPassword: String): Http[Console with Has[SlideApp], Throwable] =
    Http.collect {
      case Method.GET -> Root / "ws" =>
        Response.socket(userSocket)

      case req @ Method.GET -> Root / "ws" / "admin" =>
        req.url.query match {
          case s"password=$password" if password == adminPassword =>
            Response.socket(adminSocket)
          case _ =>
            Response.fromHttpError(HttpError.Unauthorized("INVALID PASSWORD"))
        }
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = (for {
    port   <- system.envOrElse("PORT", "8088").map(_.toInt).orElseSucceed(8088)
    _      <- SlideApp.populationStatsStream.foreach(stats => putStrLn(s"CONNECTED USERS ${stats.connectedUsers}")).fork
    _      <- putStrLn(s"STARTING SERVER ON PORT $port")
    config <- ZIO.service[Config]
    _      <- Server.start(port, app(config.adminPassword))
  } yield ())
    .provideCustomLayer(SlideApp.live ++ Config.live)
    .exitCode

  private def pickleSocket[R, E, A: Pickler](f: A => ZStream[R, E, WebSocketFrame]): Socket[Console with R, E] =
    Socket.collect {
      case WebSocketFrame.Binary(bytes) =>
        Try(Unpickle[A].fromBytes(bytes.asJava.nioBuffer())) match {
          case Failure(error) =>
            println(s"FAILED $error from $bytes")
            ZStream.fromEffect(putStrErr(s"Decoding Error: $error")).drain
          case Success(command) =>
            println(s"unpiclked $command from $bytes")
            f(command)
        }
      case other =>
        ZStream.fromEffect(UIO(println(s"RECEIVED $other"))).drain

    }
}
