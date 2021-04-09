package zio.slides

import zio.Chunk
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.slides.VoteState.{CastVoteId, Topic, UserId, Vote, VoteMap}

import java.util.UUID

// user A
// user B
// user C

// topic FavoriteLanguage
// topic DoYouLikeMe?
//       yes
//       no

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
  // Debounce on the Frontend
  // Debounce on the Server / Coalesce votes

  def empty: VoteState = new VoteState(Map.empty)

  case class CastVote(topic: Topic, vote: Vote)
  case class CastVoteId(id: UserId, topic: Topic, vote: Vote)

  object CastVoteId {
    implicit val codec: JsonCodec[CastVoteId] = DeriveJsonCodec.gen[CastVoteId]
  }

  case class Topic(string: String) extends AnyVal

  object Topic {
    implicit val codec: JsonCodec[Topic] = DeriveJsonCodec.gen[Topic]
  }

  case class UserId(string: String) extends AnyVal

  object UserId {
    implicit val codec: JsonCodec[UserId] = DeriveJsonCodec.gen[UserId]

    def random: UserId = UserId(UUID.randomUUID().toString)
  }

  case class Vote(string: String) extends AnyVal

  object Vote {
    implicit val codec: JsonCodec[Vote] = DeriveJsonCodec.gen[Vote]
  }

  type VoteMap = Map[Topic, Map[UserId, Vote]]
}
