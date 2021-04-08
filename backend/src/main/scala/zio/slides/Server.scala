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
import zio.console.{putStrErr, putStrLn}
import zio.duration.durationInt
import zio.interop.catz._
import zio.json._
import zio.magic.ZioProvideMagicOps
import zio.slides.VoteState.UserId
import zio.stream.ZStream
import zio.stream.interop.fs2z.zStreamSyntax

import java.util.UUID

/** TODO: Identity Management
  *   - Authorization
  *   - GitHub OAuth
  */
object Server extends App {
  type AppEnv     = ZEnv with Has[SlideApp]
  type AppTask[A] = RIO[AppEnv, A]

  private val DSL = Http4sDsl[AppTask]
  import DSL._

  private val routes = HttpRoutes.of[AppTask] {
    case GET -> Root =>
      Ok("Hello")

    case GET -> Root / "ws" =>
      val id = UserId(UUID.randomUUID().toString)

      //

      val toClient: fs2.Stream[AppTask, WebSocketFrame] =
        ZStream
          .mergeAllUnbounded()(
            SlideApp.slideStateStream.map[ServerCommand](ServerCommand.SendSlideState).map(s => Text(s.toJson)),
            SlideApp.questionsStream.map[ServerCommand](ServerCommand.SendAllQuestions).map(s => Text(s.toJson)),
            SlideApp.activeQuestionStream.map[ServerCommand](ServerCommand.SendActiveQuestion).map(s => Text(s.toJson)),
            ZStream.succeed[ServerCommand](ServerCommand.SendUserId(id)).map(s => Text(s.toJson)),
            SlideApp.votes
              .map[ServerCommand](ServerCommand.SendVotes)
              .map(s => Text(s.toJson)),
            ZStream.fromSchedule(Schedule.spaced(20.seconds).as(WebSocketFrame.Ping()))
          )
          .toFs2Stream

      val fromClient: Pipe[AppTask, WebSocketFrame, Unit] = _.evalMap {
        case Text(t, _) =>
          putStrLn(s"RECEIVED: $t") *>
            (t.fromJson[AppCommand] match {
              case Left(error)    => putStrErr(s"DECODING ERROR $error")
              case Right(command) => SlideApp.receive(id, command)
            })
        case f => putStrLn(s"Unknown type: $f")
      }

      putStrLn(s"CONNECTED $id") *>
        WebSocketBuilder[AppTask]
          .build(
            send = toClient,
            receive = fromClient,
            onClose = putStrLn(s"GOODBYE $id")
          )

  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    ZIO
      .runtime[AppEnv]
      .flatMap(implicit runtime =>
        for {
          port <- system.envOrElse("PORT", "8088").map(_.toInt)
          _    <- putStrLn("PORT: " + port.toString)
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
