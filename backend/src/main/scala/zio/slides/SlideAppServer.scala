package zio.slides

import boopickle.Default._
import zhttp.http._
import zhttp.service._
import zhttp.socket._
import zio._
import zio.slides.ServerCommand._
import zio.slides.VoteState.UserId
import zio.stream.ZStream

import java.nio.ByteBuffer
import scala.util.{Failure, Success, Try}

object SlideAppServer extends ZIOAppDefault {

  final case class Routes(slideApp: SlideApp, config: Config) {
    def adminSocket: SocketApp[Any] = {
      pickleSocket { (command: AdminCommand) =>
        ZStream.fromZIO(slideApp.receiveAdminCommand(command)).drain
      }
    }

    def userSocket: SocketApp[Any] = {
      val userId = UserId.random

      pickleSocket { (command: UserCommand) =>
        command match {
          case UserCommand.Subscribe =>
            ZStream
              .mergeAllUnbounded()(
                ZStream.fromZIO(slideApp.userJoined).drain,
                slideApp.slideStateStream.map(SendSlideState),
                slideApp.questionStateStream.map(SendQuestionState),
                slideApp.voteStream.map(SendVotes),
                slideApp.populationStatsStream.map(SendPopulationStats),
                ZStream.succeed[ServerCommand](SendUserId(userId))
              )
              .map { s =>
                val bytes: ByteBuffer = Pickle.intoBytes(s)
                WebSocketFrame.binary(Chunk.fromArray(bytes.array()))
              }

          case command =>
            ZStream.fromZIO(slideApp.receiveUserCommand(userId, command)).drain
        }
      }.onClose { conn =>
        // TODO: Fix zio-http onClose method not being called
        Runtime.default.unsafeRunAsync(
          ZIO.debug("Closing connection") *>
            slideApp.userLeft
        )
        ZIO.debug(s"ON CLOSE CALLED WITH CONN $conn")
      }

    }

    val app: HttpApp[Any, Throwable] =
      Http.collectZIO[Request] {
        case Method.GET -> !! / "ws" =>
          Response.fromSocketApp(userSocket)

        case req @ Method.GET -> !! / "ws" / "admin" =>
          req.url.queryParams.getOrElse("password", List.empty) match {
            case List(password) if password == config.adminPassword =>
              Response.fromSocketApp(adminSocket)
            case _ =>
              ZIO.succeed(Response.fromHttpError(HttpError.Unauthorized("INVALID PASSWORD")))
          }
      }
  }

  object Routes {
    val live = ZLayer.fromFunction(Routes.apply _)
  }

  override val run = (for {
    port <- System.envOrElse("PORT", "8088").map(_.toInt).orElseSucceed(8088)
    _ <- SlideApp.populationStatsStream
      .foreach(stats => Console.printLine(s"CONNECTED USERS ${stats.connectedUsers}"))
      .fork
    _   <- Console.printLine(s"STARTING SERVER ON PORT $port")
    app <- ZIO.serviceWith[Routes](_.app)
    _   <- Server.start(port, app)
  } yield ())
    .debug("SERVER COMPLETE")
    .provide(SlideApp.live, Config.live, Routes.live)

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
