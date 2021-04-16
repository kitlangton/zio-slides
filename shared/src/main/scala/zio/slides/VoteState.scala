package zio.slides

import zio.Chunk
import zio.slides.VoteState.{CastVoteId, Topic, Vote, VoteMap}

import java.util.UUID

case class VoteState private (map: VoteMap) { self =>
  def processUpdates(votes: Chunk[CastVoteId]): VoteState =
    votes.foldLeft(self)(_.processUpdate(_))

  def processUpdate(vote: CastVoteId): VoteState =
    VoteState {
      map.updatedWith(vote.topic) {
        case Some(idVotes) => Some(idVotes.updated(vote.id, vote.vote))
        case None          => Some(Map(vote.id -> vote.vote))
      }
    }

  def voteTotals(topic: Topic): Map[Vote, Int] =
    map.getOrElse(topic, Map.empty).toList.groupBy(_._2).view.mapValues(_.length).toMap
}

object VoteState {
  def empty: VoteState = new VoteState(Map.empty)

  case class CastVote(topic: Topic, vote: Vote)
  case class CastVoteId(id: UserId, topic: Topic, vote: Vote)

  case class Topic(string: String) extends AnyVal

  case class UserId(string: String) extends AnyVal

  object UserId {
    def random: UserId = UserId(UUID.randomUUID().toString)
  }

  case class Vote(string: String) extends AnyVal

  type VoteMap = Map[Topic, Map[UserId, Vote]]
}
