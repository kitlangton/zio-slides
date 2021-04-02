package zio.slides

import zio.slides.VoteState.{CastVoteId, Topic, UserId, Vote, VoteMap}

// user A
// user B
// user C

// topic FavoriteLanguage
// topic DoYouLikeMe?
//       yes
//       no

case class VoteState private (map: VoteMap) {

  def processUpdate(vote: CastVoteId): VoteState =
    VoteState {
      map.updatedWith(vote.topic) {
        case Some(idVotes) => Some(idVotes.updated(vote.id, vote.vote))
        case None          => Some(Map(vote.id -> vote.vote))
      }
    }

  def voteTotals(topic: Topic): Map[Vote, Int] =
    map(topic).toList.groupBy(_._2).view.mapValues(_.length).toMap
}

object VoteState {
  // Debounce on the Frontend
  // Debounce on the Server / Coalesce votes

  def empty: VoteState = new VoteState(Map.empty)

  case class CastVote(topic: Topic, vote: Vote)
  case class CastVoteId(id: UserId, topic: Topic, vote: Vote)

  case class Topic(string: String)  extends AnyVal
  case class UserId(string: String) extends AnyVal
  case class Vote(string: String)   extends AnyVal

  type VoteMap = Map[Topic, Map[UserId, Vote]]
}
