package zio.slides

import zio._
import zio.clock.Clock
import zio.console.Console
import zio.duration.durationInt
import zio.slides.VoteState.{CastVoteId, UserId}
import zio.stream._

/** Improvements:
  *
  * - Add a VoteStateRef. Send the current Vote State to a user
  *   when they join.
  *
  * - When a user disconnects, remove their votes.
  */
trait SlideApp {
  def slideStateStream: UStream[SlideState]
  def questionStateStream: UStream[QuestionState]
  def voteStream: UStream[Chunk[CastVoteId]]
  def populationStatsStream: UStream[PopulationStats]

  def receive(id: UserId, appCommand: ClientCommand): UIO[Unit]

  def userJoined: UIO[Unit]
  def userLeft: UIO[Unit]
}

object SlideApp {
  val live: URLayer[Console with Clock, Has[SlideApp]] = SlideAppLive.layer

  // Accessor Methods

  def slideStateStream: ZStream[Has[SlideApp], Nothing, SlideState] =
    ZStream.accessStream[Has[SlideApp]](_.get.slideStateStream)

  def questionStateStream: ZStream[Has[SlideApp], Nothing, QuestionState] =
    ZStream.accessStream[Has[SlideApp]](_.get.questionStateStream)

  def voteStream: ZStream[Has[SlideApp], Nothing, Chunk[CastVoteId]] =
    ZStream.accessStream[Has[SlideApp]](_.get.voteStream)

  def populationStatsStream: ZStream[Has[SlideApp], Nothing, PopulationStats] =
    ZStream.accessStream[Has[SlideApp]](_.get.populationStatsStream)

  def receive(id: UserId, appCommand: ClientCommand): ZIO[Has[SlideApp], Nothing, Unit] =
    ZIO.accessM[Has[SlideApp]](_.get.receive(id, appCommand))

  def userJoined: ZIO[Has[SlideApp], Nothing, Unit] =
    ZIO.accessM[Has[SlideApp]](_.get.userJoined)

  def userLeft: ZIO[Has[SlideApp], Nothing, Unit] =
    ZIO.accessM[Has[SlideApp]](_.get.userLeft)
}

case class SlideAppLive(
    slideStateRef: RefM[SlideState],
    slideStateStream: UStream[SlideState],
    questionStateRef: RefM[QuestionState],
    questionStateStream: UStream[QuestionState],
    voteQueue: Queue[CastVoteId],
    voteStream: UStream[Chunk[CastVoteId]],
    populationStatsRef: RefM[PopulationStats],
    populationStatsStream: UStream[PopulationStats]
) extends SlideApp {

  override def receive(id: UserId, appCommand: ClientCommand): UIO[Unit] = appCommand match {
    case command: AdminCommand => receive(command)
    case command: UserCommand  => receive(id, command)
  }

  private def receive(adminCommand: AdminCommand): UIO[Unit] =
    adminCommand match {
      case AdminCommand.NextSlide => slideStateRef.update(s => UIO(s.nextSlide))
      case AdminCommand.PrevSlide => slideStateRef.update(s => UIO(s.prevSlide))
      case AdminCommand.NextStep  => slideStateRef.update(s => UIO(s.nextStep))
      case AdminCommand.PrevStep  => slideStateRef.update(s => UIO(s.prevStep))
      case AdminCommand.ToggleQuestion(id) =>
        questionStateRef.update(qs => UIO(qs.toggleQuestion(id)))
    }

  private def receive(id: UserId, userCommand: UserCommand): UIO[Unit] =
    userCommand match {
      case UserCommand.ConnectionPlease() =>
        ZIO.unit
      case UserCommand.AskQuestion(question, slideIndex) =>
        questionStateRef.update(qs => UIO(qs.askQuestion(question, slideIndex)))
      case UserCommand.SendVote(topic, vote) =>
        voteQueue.offer(CastVoteId(id, topic, vote)).unit
    }

  override def userLeft: UIO[Unit] =
    populationStatsRef.update(stats => UIO(stats.removeOne))

  override def userJoined: UIO[Unit] =
    populationStatsRef.update(stats => UIO(stats.addOne))
}

object SlideAppLive {
  val layer: URLayer[Console with Clock, Has[SlideApp]] = {
    for {
      slideVar           <- Var.make(SlideState.empty).toManaged_
      questionsVar       <- Var.make(QuestionState.empty).toManaged_
      populationStatsVar <- Var.make(PopulationStats.empty).toManaged_

      voteQueue <- Queue.bounded[CastVoteId](256).toManaged_
      voteStream <- ZStream
        .fromQueue(voteQueue)
        .groupedWithin(100, 300.millis)
        .broadcastDynamic(10)

    } yield SlideAppLive(
      slideStateRef = slideVar.ref,
      slideStateStream = slideVar.changes,
      questionStateRef = questionsVar.ref,
      questionStateStream = questionsVar.changes,
      voteQueue = voteQueue,
      voteStream = ZStream.unwrapManaged(voteStream),
      populationStatsRef = populationStatsVar.ref,
      populationStatsStream = populationStatsVar.changes
    )
  }.toLayer
}

final class Var[A] private (val ref: RefM[A], val changes: Stream[Nothing, A])

object Var {

  /** Creates a new `Var` with the specified value.
    */
  def make[A](a: A): UIO[Var[A]] =
    for {
      ref <- RefM.make(a)
      hub <- Hub.sliding[A](512)
      changes = ZStream.unwrapManaged {
        for {
          a     <- ref.get.toManaged_
          queue <- hub.subscribe
        } yield ZStream(a) ++ ZStream.fromQueue(queue)
      }
    } yield new Var(ref.tapInput(hub.publish), changes)
}
