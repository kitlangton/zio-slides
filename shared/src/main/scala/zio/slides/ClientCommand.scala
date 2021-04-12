package zio.slides

import zio.json.{DeriveJsonCodec, JsonCodec}

import java.util.UUID

sealed trait ClientCommand

object ClientCommand {
  implicit val codec: JsonCodec[ClientCommand] = DeriveJsonCodec.gen[ClientCommand]
}

sealed trait UserCommand extends ClientCommand

object UserCommand {
  case class AskQuestion(question: String, slideIndex: SlideIndex)  extends UserCommand
  case class SendVote(topic: VoteState.Topic, vote: VoteState.Vote) extends UserCommand

  implicit val codec: JsonCodec[UserCommand] = DeriveJsonCodec.gen[UserCommand]
}

sealed trait AdminCommand extends ClientCommand

object AdminCommand {
  case object NextSlide               extends AdminCommand
  case object PrevSlide               extends AdminCommand
  case object NextStep                extends AdminCommand
  case object PrevStep                extends AdminCommand
  case class ToggleQuestion(id: UUID) extends AdminCommand

  implicit val codec: JsonCodec[AdminCommand] = DeriveJsonCodec.gen[AdminCommand]
}
