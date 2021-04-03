package zio.slides

import zio.Chunk
import zio.json._

import java.util.UUID

sealed trait ServerCommand

object ServerCommand {
  case class SendSlideState(slideState: SlideState)           extends ServerCommand
  case class SendAllQuestions(questions: Vector[Question])    extends ServerCommand
  case class SendActiveQuestion(activeQuestion: Option[UUID]) extends ServerCommand
  case class SendVotes(votes: Chunk[VoteState.CastVoteId])    extends ServerCommand
  case class SendUserId(id: VoteState.UserId)                 extends ServerCommand

  implicit val uuidCodec                       = JsonCodec(JsonEncoder.uuid, JsonDecoder.uuid)
  implicit val codec: JsonCodec[ServerCommand] = DeriveJsonCodec.gen[ServerCommand]

}

sealed trait AppCommand

object AppCommand {
  implicit val uuidCodec                    = JsonCodec(JsonEncoder.uuid, JsonDecoder.uuid)
  implicit val codec: JsonCodec[AppCommand] = DeriveJsonCodec.gen[AppCommand]
}

sealed trait AdminCommand extends AppCommand

object AdminCommand {
  case object NextSlide               extends AdminCommand
  case object PrevSlide               extends AdminCommand
  case object NextStep                extends AdminCommand
  case object PrevStep                extends AdminCommand
  case class ToggleQuestion(id: UUID) extends AdminCommand
}

sealed trait UserCommand extends AppCommand

object UserCommand {
  case class AskQuestion(question: String, slideIndex: SlideIndex)  extends UserCommand
  case class SendVote(topic: VoteState.Topic, vote: VoteState.Vote) extends UserCommand
}

case class SlideState(slideIndex: Int, slideStepMap: Map[Int, Int]) {
  def stepIndex: Int = stepForSlide(slideIndex)

  def prevSlide: SlideState = copy(slideIndex = (slideIndex - 1) max 0)
  def nextSlide: SlideState = copy(slideIndex = slideIndex + 1)

  def prevStep: SlideState =
    copy(slideStepMap = slideStepMap.updated(slideIndex, Math.max(0, stepForSlide(slideIndex) - 1)))
  def nextStep: SlideState =
    copy(slideStepMap = slideStepMap.updated(slideIndex, stepForSlide(slideIndex) + 1))

  def stepForSlide(index: Int): Int =
    slideStepMap.getOrElse(index, 0)
}

object SlideState {
  def empty: SlideState = SlideState(0, Map.empty)

  implicit val intMapEncoder: JsonFieldEncoder[Int] = JsonFieldEncoder.string.contramap[Int](_.toString)
  implicit val intMapDecoder: JsonFieldDecoder[Int] = JsonFieldDecoder.string.map(_.toIntOption.getOrElse(0))

  implicit val intMapCodec: JsonCodec[Map[Int, Int]] =
    JsonCodec.map[Int, Int]

  implicit val codec: JsonCodec[SlideState] =
    DeriveJsonCodec.gen[SlideState]

  def random: SlideState = {
    val randomSlide = scala.util.Random.nextInt(3)
    val randomStep  = scala.util.Random.nextInt(3)
    SlideState(randomSlide, Map(randomSlide -> randomStep))
  }
}
