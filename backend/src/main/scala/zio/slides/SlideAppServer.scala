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

  def adminSocket: SocketApp[Has[SlideApp] with Console, Nothing] =
    pickleSocket { (command: AdminCommand) =>
      ZStream.fromEffect(SlideApp.receiveAdminCommand(command)).drain
    }

  def userSocket: SocketApp[Console with Has[SlideApp], Nothing] = {
    val userId = UserId.random

    val handleCommand = pickleSocket { (command: UserCommand) =>
      command match {
        case UserCommand.Subscribe =>
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
              val byteBuf           = Unpooled.wrappedBuffer(bytes)
              WebSocketFrame.binary(ByteBuf(byteBuf))
            }

        case command =>
          ZStream.fromEffect(SlideApp.receiveUserCommand(userId, command)).drain
      }
    }

    handleCommand
  }

  private def app(adminPassword: String): HttpApp[Console with Has[SlideApp], Throwable] =
    HttpApp.collect {
      case Method.GET -> Root / "ws" =>
        Response.socket(userSocket)

      case req @ Method.GET -> Root / "ws" / "admin" =>
        req.url.queryParams.getOrElse("password", List.empty) match {
          case List(password) if password == adminPassword =>
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

  private def pickleSocket[R, E, A: Pickler](f: A => ZStream[R, E, WebSocketFrame]): SocketApp[Console with R, E] =
    SocketApp.message(
      Socket.collect {
        case WebSocketFrame.Binary(bytes) =>
          Try(Unpickle[A].fromBytes(bytes.asJava.nioBuffer())) match {
            case Failure(error) =>
              ZStream.fromEffect(putStrErr(s"Decoding Error: $error").!).drain
            case Success(command) =>
              f(command)
          }
        case other =>
          ZStream.fromEffect(UIO(println(s"RECEIVED $other"))).drain
      }
    )
}
