package zio.slides

import zio.Chunk
import zio.json.{DeriveJsonCodec, JsonCodec}

sealed trait ServerCommand

object ServerCommand {
  case class SendSlideState(slideState: SlideState)                extends ServerCommand
  case class SendQuestionState(questionState: QuestionState)       extends ServerCommand
  case class SendVotes(votes: Chunk[VoteState.CastVoteId])         extends ServerCommand
  case class SendUserId(id: VoteState.UserId)                      extends ServerCommand
  case class SendPopulationStats(populationStats: PopulationStats) extends ServerCommand

  implicit val codec: JsonCodec[ServerCommand] = DeriveJsonCodec.gen[ServerCommand]

}
