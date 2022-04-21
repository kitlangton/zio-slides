package zio.slides

import boopickle.Default._
import io.netty.buffer.Unpooled
import zhttp.http._
import zhttp.service._
import zhttp.socket._
import zio._
import zio.slides.ServerCommand._
import zio.slides.VoteState.UserId
import zio.stream.ZStream

import java.nio.ByteBuffer
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.{Failure, Success, Try}

object SlideAppServer extends ZIOAppDefault {

  def adminSocket: SocketApp[SlideApp] = {
    pickleSocket { (command: AdminCommand) =>
      ZStream.fromZIO(SlideApp.receiveAdminCommand(command)).drain
    }
  }

  def userSocket: SocketApp[SlideApp] = {
    val userId = UserId.random

    val handleCommand = pickleSocket { (command: UserCommand) =>
      command match {
        case UserCommand.Subscribe =>
          ZStream
            .mergeAllUnbounded()(
              ZStream.fromZIO(SlideApp.userJoined).drain,
              SlideApp.slideStateStream.map(SendSlideState),
              SlideApp.questionStateStream.map(SendQuestionState),
              SlideApp.voteStream.map(SendVotes),
              SlideApp.populationStatsStream.map(SendPopulationStats),
              ZStream.succeed[ServerCommand](SendUserId(userId))
            )
            .map { s =>
              val bytes: ByteBuffer = Pickle.intoBytes(s)
              WebSocketFrame.binary(Chunk.fromArray(bytes.array()))
            }

        case command =>
          ZStream.fromZIO(SlideApp.receiveUserCommand(userId, command)).drain
      }
    }

    handleCommand
  }

  private def app(adminPassword: String): HttpApp[SlideApp, Throwable] =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "ws" =>
        Response.fromSocketApp(userSocket)

      case req @ Method.GET -> !! / "ws" / "admin" =>
        req.url.queryParams.getOrElse("password", List.empty) match {
          case List(password) if password == adminPassword =>
            Response.fromSocketApp(adminSocket)
          case _ =>
            ZIO.succeed(Response.fromHttpError(HttpError.Unauthorized("INVALID PASSWORD")))
        }
    }

  override val run = (for {
    port <- System.envOrElse("PORT", "8088").map(_.toInt).orElseSucceed(8088)
    _ <- SlideApp.populationStatsStream
      .foreach(stats => Console.printLine(s"CONNECTED USERS ${stats.connectedUsers}"))
      .fork
    _      <- Console.printLine(s"STARTING SERVER ON PORT $port")
    config <- ZIO.service[Config]
    _      <- Server.start(port, app(config.adminPassword))
  } yield ())
    .provide(SlideApp.live, Config.live)
    .exitCode

  private def pickleSocket[R, E <: Throwable, A: Pickler](f: A => ZStream[R, E, WebSocketFrame]): SocketApp[R] =
    SocketApp(
      Socket.collect[WebSocketFrame] {
        case WebSocketFrame.Binary(bytes) =>
          Try(Unpickle[A].fromBytes(ByteBuffer.wrap(bytes.toArray))) match {
            case Failure(error) =>
              ZStream.fromZIO(Console.printError(s"Decoding Error: $error").!).drain
            case Success(command) =>
              f(command)
          }
        case other =>
          ZStream.fromZIO(ZIO.succeed(println(s"RECEIVED $other"))).drain
      }
    )
}
