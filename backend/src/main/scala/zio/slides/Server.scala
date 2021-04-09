package zio.slides

import fs2.Pipe
import org.http4s._
import org.http4s.dsl._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.CORS
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame.Text
import org.http4s.websocket._
import zio._
import zio.console.{putStr, putStrErr, putStrLn}
import zio.duration.durationInt
import zio.interop.catz._
import zio.json._
import zio.magic.ZioProvideMagicOps
import zio.slides.ServerCommand.{SendPopulationStats, SendQuestionState, SendSlideState, SendUserId, SendVotes}
import zio.slides.VoteState.UserId
import zio.stream.ZStream
import zio.stream.interop.fs2z.zStreamSyntax

object Server extends App {
  type AppEnv     = ZEnv with Has[SlideApp]
  type AppTask[A] = RIO[AppEnv, A]

  private val DSL = Http4sDsl[AppTask]
  import DSL._

  private val routes = HttpRoutes.of[AppTask] {
    case GET -> Root =>
      Ok("Howdy!")

    case GET -> Root / "ws" =>
      val userId = UserId.random

      val toClient: fs2.Stream[AppTask, WebSocketFrame] =
        ZStream
          .mergeAllUnbounded()(
            SlideApp.slideStateStream.map[ServerCommand](SendSlideState).map(s => Text(s.toJson)),
            SlideApp.questionStateStream.map[ServerCommand](SendQuestionState).map(s => Text(s.toJson)),
            SlideApp.voteStream.map[ServerCommand](SendVotes).map(s => Text(s.toJson)),
            SlideApp.populationStatsStream.map[ServerCommand](SendPopulationStats).map(s => Text(s.toJson)),
            ZStream.succeed[ServerCommand](SendUserId(userId)).map(s => Text(s.toJson)),
            ZStream.fromSchedule(Schedule.spaced(20.seconds).as(WebSocketFrame.Ping()))
          )
          .toFs2Stream

      val fromClient: Pipe[AppTask, WebSocketFrame, Unit] = _.evalMap {
        case Text(t, _) =>
          t.fromJson[ClientCommand] match {
            case Left(error) =>
              putStrErr(s"DECODING ERROR $error")
            case Right(command) =>
              println(s"RECEIVED COMMAND $command")
              SlideApp.receive(userId, command)
          }
        case f =>
          putStrLn(s"Unknown type: $f")
      }
        .handleErrorWith { throwable =>
          ZStream.fromEffect(putStrLn(s"OH NO: $throwable")).toFs2Stream
        }

      putStrLn(s"USER JOINED: $userId") *>
        SlideApp.userJoined *>
        WebSocketBuilder[AppTask]
          .build(
            send = toClient,
            receive = fromClient,
            onClose = SlideApp.userLeft *> putStrLn(s"USER LEFT: $userId")
          )
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    ZIO
      .runtime[AppEnv]
      .flatMap(implicit runtime =>
        for {
          port <- system.envOrElse("PORT", "8088").map(_.toInt)
          _    <- putStrLn(s"STARTING SERVER ON PORT $port")
          _ <- BlazeServerBuilder[AppTask](runtime.platform.executor.asEC)
            .bindHttp(port, "0.0.0.0")
            .withHttpApp(
              Router[AppTask]("/" -> CORS(routes)).orNotFound
            )
            .resource
            .toManagedZIO
            .useForever
        } yield ()
      )
      .injectCustom(SlideApp.live)
      .exitCode
}
