package zio.slides


sealed trait ServerCommand

object ServerCommand {
  case class SendSlideState(slideState: SlideState)                extends ServerCommand
  case class SendQuestionState(questionState: QuestionState)       extends ServerCommand
  case class SendVotes(votes: Vector[VoteState.CastVoteId])        extends ServerCommand
  case class SendUserId(id: VoteState.UserId)                      extends ServerCommand
  case class SendPopulationStats(populationStats: PopulationStats) extends ServerCommand
}
