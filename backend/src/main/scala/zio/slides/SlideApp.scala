package zio.slides

import zio._
import zio.clock.Clock
import zio.console.{Console, putStrLn}
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

  def receive(id: UserId, appCommand: ClientCommand): UIO[Unit]
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

  def receive(id: UserId, appCommand: ClientCommand): ZIO[Has[SlideApp], Nothing, Unit] =
    ZIO.accessM[Has[SlideApp]](_.get.receive(id, appCommand))
}

case class SlideAppLive(
    slideStateRef: RefM[SlideState],
    slideStateStream: UStream[SlideState],
    questionStateRef: RefM[QuestionState],
    questionStateStream: UStream[QuestionState],
    voteQueue: Queue[CastVoteId],
    voteStream: UStream[Chunk[CastVoteId]]
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
      case UserCommand.AskQuestion(question, slideIndex) =>
        questionStateRef.update(qs => UIO(qs.askQuestion(question, slideIndex)))
      case UserCommand.SendVote(topic, vote) =>
        voteQueue.offer(CastVoteId(id, topic, vote)).unit
    }
}

object SlideAppLive {
  val layer: URLayer[Console with Clock, Has[SlideApp]] = {
    for {
      slideVar     <- HubLikeSubscriptionRef.make(SlideState.empty).toManaged_
      questionsVar <- HubLikeSubscriptionRef.make(QuestionState.empty).toManaged_

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
      voteStream = ZStream.unwrap(voteStream)
    )
  }.toLayer
}

final class HubLikeSubscriptionRef[A] private (val ref: RefM[A], val changes: Stream[Nothing, A])

object HubLikeSubscriptionRef {
  def make[A](a: A): UIO[HubLikeSubscriptionRef[A]] =
    for {
      ref <- RefM.make(a)
      hub <- Hub.unbounded[A]
      changes = ZStream.unwrapManaged {
        ZManaged {
          ref.modify { a =>
            ZIO.succeedNow(a).zipWith(hub.subscribe.zio) { case (a, (finalizer, queue)) =>
              (finalizer, ZStream(a) ++ ZStream.fromQueue(queue))
            } <*> ZIO.succeedNow(a)
          }.uninterruptible
        }
      }
    } yield new HubLikeSubscriptionRef(ref.tapInput(hub.publish), changes)
}
