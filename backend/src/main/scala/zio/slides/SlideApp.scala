package zio.slides

import zio._
import zio.Clock
import zio.slides.VoteState.{CastVoteId, UserId}
import zio.stream._
import zio.Console

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
  def voteStream: UStream[Vector[CastVoteId]]
  def populationStatsStream: UStream[PopulationStats]

  def receiveUserCommand(id: UserId, userCommand: UserCommand): UIO[Unit]
  def receiveAdminCommand(adminCommand: AdminCommand): UIO[Unit]

  def userJoined: UIO[Unit]
  def userLeft: UIO[Unit]
}

object SlideApp {
  val live: ZLayer[Any, Nothing, SlideApp] = SlideAppLive.layer

  // Accessor Methods

  def slideStateStream: ZStream[SlideApp, Nothing, SlideState] =
    ZStream.environmentWithStream[SlideApp](_.get.slideStateStream)

  def questionStateStream: ZStream[SlideApp, Nothing, QuestionState] =
    ZStream.environmentWithStream[SlideApp](_.get.questionStateStream)

  def voteStream: ZStream[SlideApp, Nothing, Vector[CastVoteId]] =
    ZStream.environmentWithStream[SlideApp](_.get.voteStream)

  def populationStatsStream: ZStream[SlideApp, Nothing, PopulationStats] =
    ZStream.environmentWithStream[SlideApp](_.get.populationStatsStream)

  def receiveUserCommand(id: UserId, userCommand: UserCommand): ZIO[SlideApp, Nothing, Unit] =
    ZIO.environmentWithZIO[SlideApp](_.get.receiveUserCommand(id, userCommand))

  def receiveAdminCommand(adminCommand: AdminCommand): ZIO[SlideApp, Nothing, Unit] =
    ZIO.environmentWithZIO[SlideApp](_.get.receiveAdminCommand(adminCommand))

  def userJoined: ZIO[SlideApp, Nothing, Unit] =
    ZIO.environmentWithZIO[SlideApp](_.get.userJoined)

  def userLeft: ZIO[SlideApp, Nothing, Unit] =
    ZIO.environmentWithZIO[SlideApp](_.get.userLeft)
}

case class SlideAppLive(
    slideStateRef: Ref[SlideState],
    slideStateStream: UStream[SlideState],
    questionStateRef: Ref[QuestionState],
    questionStateStream: UStream[QuestionState],
    voteQueue: Queue[CastVoteId],
    voteStream: UStream[Vector[CastVoteId]],
    populationStatsRef: Ref[PopulationStats],
    populationStatsStream: UStream[PopulationStats]
) extends SlideApp {

  def receiveAdminCommand(adminCommand: AdminCommand): UIO[Unit] =
    adminCommand match {
      case AdminCommand.NextSlide => slideStateRef.update(_.nextSlide)
      case AdminCommand.PrevSlide => slideStateRef.update(_.prevSlide)
      case AdminCommand.NextStep  => slideStateRef.update(_.nextStep)
      case AdminCommand.PrevStep  => slideStateRef.update(_.prevStep)
      case AdminCommand.ToggleQuestion(id) =>
        questionStateRef.update(_.toggleQuestion(id))
    }

  def receiveUserCommand(id: UserId, userCommand: UserCommand): UIO[Unit] =
    userCommand match {
      case UserCommand.AskQuestion(question, slideIndex) =>
        questionStateRef.update(_.askQuestion(question, slideIndex))
      case UserCommand.SendVote(topic, vote) =>
        voteQueue.offer(CastVoteId(id, topic, vote)).unit
      case UserCommand.Subscribe =>
        UIO.unit
    }

  override def userLeft: UIO[Unit] =
    populationStatsRef.update(_.removeOne)

  override def userJoined: UIO[Unit] =
    populationStatsRef.update(_.addOne)
}

object SlideAppLive {
  val layer: ZLayer[Any, Nothing, SlideApp] = ZLayer.scoped {
    for {
      slideVar           <- SubscriptionRef.make(SlideState.empty)
      questionsVar       <- SubscriptionRef.make(QuestionState.empty)
      populationStatsVar <- SubscriptionRef.make(PopulationStats(0))

      voteQueue  <- Queue.bounded[CastVoteId](256)
      voteStream <- ZStream.fromQueue(voteQueue).groupedWithin(100, 300.millis).broadcastDynamic(128)
    } yield SlideAppLive(
      slideStateRef = slideVar,
      slideStateStream = slideVar.changes,
      questionStateRef = questionsVar,
      questionStateStream = questionsVar.changes,
      voteQueue = voteQueue,
      voteStream = voteStream.map(_.toVector),
      populationStatsRef = populationStatsVar,
      populationStatsStream = populationStatsVar.changes
    )
  }
}
