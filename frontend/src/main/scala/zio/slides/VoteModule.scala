package zio.slides

import animus.SignalOps
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

  def VotesView(
      topic: VoteState.Topic = exampleTopic,
      options: List[String] = exampleOptions,
      $active: Signal[Boolean] = Val(true)
  ): Div =
    div(
      transform <-- $active.map { if (_) 1.0 else 0.95 }.spring.map { s => s"scale($s)" },
      border("1px solid orange"),
      borderWidth <-- $active.map { if (_) 0.0 else 2.0 }.spring.px,
      div(
        "DONE",
        display.flex,
        alignItems.center,
        justifyContent.center,
        fontWeight.bold,
        fontSize.medium,
        background("orange"),
        overflowY.hidden,
        height <-- $active.map { if (_) 0.0 else 28.0 }.spring.px
      ),
      options.map { string =>
        VoteView(topic, VoteState.Vote(string), $active)
      }
    )

  private def VoteView(topic: VoteState.Topic, vote: VoteState.Vote, $active: Signal[Boolean]): Div = {
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
      composeEvents(onMouseEnter)(_.withCurrentValueOf($active).filter(_._2)) --> { _ =>
        voteBus.emit(UserCommand.SendVote(topic, vote))
        userIdVar.now().foreach { userId =>
          voteStateVar.update(_.processUpdate(VoteState.CastVoteId(userId, topic, vote)))
        }
      }
    )
  }
}
