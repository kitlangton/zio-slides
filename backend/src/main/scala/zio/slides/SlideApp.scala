package zio.slides

import zio._
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.duration.durationInt
import zio.slides.VoteState.{CastVoteId, UserId}
import zio.stream.{SubscriptionRef, UStream, ZStream}

import java.util.UUID

trait SlideApp {
  def slideState: UStream[SlideState]
  def activeQuestion: UStream[Option[UUID]]
  def allQuestions: UStream[Vector[Question]]

  def votes: UStream[Chunk[CastVoteId]]

  def receive(id: UserId, appCommand: AppCommand): UIO[Unit]
}

object SlideApp {
  val live: URLayer[Console with Clock, Has[SlideApp]] = {
    for {
      _ <- putStrLn("STARTING UP SLIDE APP")
        .toManaged(_ => putStrLn("SHUTTING DOWN SLIDE APP"))

      // Slide State
      // TODO: Use SubscriptionRef
      slideQueue  <- Queue.bounded[SlideState](256).toManaged_
      slideRef    <- RefM.make(SlideState.empty).map(_.tapInput(slideQueue.offer)).toManaged_
      slideStream <- ZStream.fromQueue(slideQueue).broadcastDynamic(10)

      // Questions
      questionQueue <- Queue.bounded[QuestionState](256).toManaged_
      questionRef   <- RefM.make(QuestionState.empty).map(_.tapInput(questionQueue.offer)).toManaged_
      questionStream0 <- ZStream
        .fromQueue(questionQueue)
        .broadcastDynamic(10)
      questionStream = ZStream.fromEffect(questionRef.get) ++ ZStream.fromEffect(questionStream0).flatten

      // Votes
      voteQueue <- Queue.bounded[CastVoteId](256).toManaged_
//      voteRef    <- Ref.make(VoteState.empty).toManaged_
      voteStream <- ZStream
        .fromQueue(voteQueue)
        .groupedWithin(100, 300.millis)
        .broadcastDynamic(10)
    } yield SlideAppLive(
      slideStateRef = slideRef,
      slideState = ZStream.fromEffect(slideRef.get) ++ ZStream.fromEffect(slideStream).flatten,
      questionStateRef = questionRef,
      activeQuestion = questionStream.map(_.activeQuestionId),
      allQuestions = questionStream.map(_.questions),
      voteQueue = voteQueue,
      votes = ZStream.unwrap(voteStream)
    )
  }.toLayer

  // Accessor Methods

  def slideStateStream: ZStream[Has[SlideApp], Nothing, SlideState] =
    ZStream.accessStream[Has[SlideApp]](_.get.slideState)

  def activeQuestionStream: ZStream[Has[SlideApp], Nothing, Option[UUID]] =
    ZStream.accessStream[Has[SlideApp]](_.get.activeQuestion)

  def questionsStream: ZStream[Has[SlideApp], Nothing, Vector[Question]] =
    ZStream.accessStream[Has[SlideApp]](_.get.allQuestions)

  def votes: ZStream[Has[SlideApp], Nothing, Chunk[CastVoteId]] =
    ZStream.accessStream[Has[SlideApp]](_.get.votes)

  def receive(id: UserId, appCommand: AppCommand): ZIO[Has[SlideApp], Nothing, Unit] =
    ZIO.accessM[Has[SlideApp]](_.get.receive(id, appCommand))
}

case class SlideAppLive(
    slideStateRef: RefM[SlideState],
    slideState: UStream[SlideState],
    questionStateRef: RefM[QuestionState],
    activeQuestion: UStream[Option[UUID]],
    allQuestions: UStream[Vector[Question]],
    voteQueue: Queue[CastVoteId],
    votes: UStream[Chunk[CastVoteId]]
) extends SlideApp {
  override def receive(id: UserId, appCommand: AppCommand): UIO[Unit] = appCommand match {
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
