package zio.slides

import zio._
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.stream.ZStream

import java.util.UUID

trait SlideApp {
  def slideState: ZStream[Any, Nothing, SlideState]
  def activeQuestion: ZStream[Any, Nothing, Option[UUID]]
  def allQuestions: ZStream[Any, Nothing, Vector[Question]]

  def receive(appCommand: AppCommand): UIO[Unit]
}

object SlideApp {

  val live: URLayer[Console with Clock, Has[SlideApp]] = {
    for {
      _ <- putStrLn("STARTING SLIDE APP").toManaged_
      _ <- ZManaged.finalizer(putStrLn("SHUTTING DOWN SLIDE APP"))

      // Slide State
      slideQueue  <- Queue.bounded[SlideState](256).toManaged_
      slideRef    <- RefM.make(SlideState.empty).map(_.tapInput(slideQueue.offer)).toManaged_
      slideStream <- ZStream.fromQueue(slideQueue).broadcastDynamic(10)

      // Questions
      questionQueue   <- Queue.bounded[QuestionState](256).toManaged_
      questionRef     <- RefM.make(QuestionState.empty).map(_.tapInput(questionQueue.offer)).toManaged_
      questionStream0 <- ZStream.fromQueue(questionQueue).broadcastDynamic(10)
      questionStream = ZStream.fromEffect(questionRef.get) ++ ZStream.fromEffect(questionStream0).flatten
    } yield SlideAppLive(
      slideStateRef = slideRef,
      slideState = ZStream.fromEffect(slideRef.get) ++ ZStream.fromEffect(slideStream).flatten,
      questionStateRef = questionRef,
      activeQuestion = questionStream.map(_.activeQuestion),
      allQuestions = questionStream.map(_.questions)
    )
  }.toLayer
}

case class SlideAppLive(
    slideStateRef: RefM[SlideState],
    slideState: ZStream[Any, Nothing, SlideState],
    questionStateRef: RefM[QuestionState],
    activeQuestion: ZStream[Any, Nothing, Option[UUID]],
    allQuestions: ZStream[Any, Nothing, Vector[Question]]
) extends SlideApp {
  override def receive(appCommand: AppCommand): UIO[Unit] = appCommand match {
    case command: AdminCommand => receive(command)
    case command: UserCommand  => receive(command)
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

  private def receive(userCommand: UserCommand): UIO[Unit] =
    userCommand match {
      case UserCommand.AskQuestion(question, slideIndex) =>
        questionStateRef.update(qs => UIO(qs.askQuestion(question, slideIndex)))
    }
}
