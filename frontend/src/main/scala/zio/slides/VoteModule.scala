package zio.slides

import com.raquo.laminar.api.L._
import zio.slides.State.{userIdVar, voteStateVar}

object VoteModule {
  lazy val voteBus: EventBus[UserCommand.SendVote] = new EventBus

  lazy val exampleOptions: List[String] = List(
    "Full Stack Architecture",
    "Scala.js & Laminar",
    "WebSocket Communication",
    ""
  )

  lazy val exampleTopic: VoteState.Topic = VoteState.Topic("Scala-Topic")

  def VotesView(topic: VoteState.Topic = exampleTopic, options: List[String] = exampleOptions): Div =
    div(
      options.map { string =>
        VoteView(topic, VoteState.Vote(string))
      }
    )

  private def VoteView(topic: VoteState.Topic, vote: VoteState.Vote): Div = {
    val $totalVotes        = voteStateVar.signal.map(_.voteTotals(topic).getOrElse(vote, 0))
    val $numberOfCastVotes = voteStateVar.signal.map(_.voteTotals(topic).values.sum)

    val $votePercentage = $totalVotes.combineWithFn($numberOfCastVotes) { (votes, all) =>
      if (all == 0) 0.0
      else votes.toDouble / all.toDouble
    }

    div(
      padding("8px"),
      border("1px solid #444"),
      position.relative,
      background("#223"),
      div(
        cls("vote-vote"),
        position.absolute,
        zIndex(1),
        left("0"),
        top("0"),
        right("0"),
        bottom("0"),
        width <-- $votePercentage.map(d => s"${d * 100}%"),
        background("blue")
      ),
      div(
        position.relative,
        vote.string,
        " ",
        span(
          opacity(0.6),
          child.text <-- $totalVotes
        ),
        zIndex(2)
      ),
      onMouseEnter --> { _ =>
        voteBus.emit(UserCommand.SendVote(topic, vote))
        userIdVar.now().foreach { userId =>
          voteStateVar.update(_.processUpdate(VoteState.CastVoteId(userId, topic, vote)))
        }
      }
    )
  }
}
