package zio.slides

import com.raquo.laminar.api.L._
import io.laminext.websocket._
import io.laminext.websocket.zio._

import scala.concurrent.duration.DurationInt
import scala.util.Try
import State._
import Styles._

object State {
  val slideStateVar: Var[SlideState]           = Var(SlideState.empty)
  val questionStateVar: Var[QuestionState]     = Var(QuestionState.empty)
  val voteStateVar                             = Var(VoteState.empty)
  val userIdVar: Var[Option[VoteState.UserId]] = Var(Option.empty[VoteState.UserId])
}

object Slides {
  val ws: WebSocket[ServerCommand, UserCommand] =
    WebSocket
      .url(Config.webSocketsUrl)
      .json[ServerCommand, UserCommand]
      .build(reconnectRetries = Int.MaxValue, reconnectDelay = 3.seconds)

  val slideIndexOverride: Var[Option[SlideIndex]] = Var(None)
  val isAskingVar                                 = Var(Option.empty[SlideIndex])
  val populationStatsVar: Var[PopulationStats]    = Var(PopulationStats(1))

  def BottomPanel: Div =
    div(
      position.fixed,
      zIndex(2),
      bottom("0"),
      left("0"),
      right("0"),
      windowEvents.onKeyDown.filter(k => k.key == "q" && k.ctrlKey) --> { _ =>
        val state = slideStateVar.now()
        isAskingVar.update {
          case Some(_) => None
          case None    => Some(SlideIndex(state.slideIndex, state.stepIndex))
        }
      },
      questionStateVar.signal --> { state =>
        val question = state.questions.find(q => state.activeQuestionId.contains(q.id))
        question match {
          case Some(question) => slideIndexOverride.set(Some(question.slideIndex))
          case None           => slideIndexOverride.set(None)
        }
      },
      ActiveQuestion,
      AskQuestion,
      Admin.AdminPanel
    )

  private def AskQuestion = {
    val questionVar = Var("")

    def submitQuestion(): Unit =
      isAskingVar.now().foreach { index =>
        val question = questionVar.now()
        if (question.nonEmpty) {
          ws.sendOne(UserCommand.AskQuestion(question, index))
          isAskingVar.set(None)
          questionVar.set("")
        }
      }

    div(
      panelStyles(isAskingVar.signal.map(_.isDefined)),
      textAlign.left,
      div(
        display.flex,
        fontStyle.italic,
        fontSize.medium,
        opacity(0.7),
        "ASK A QUESTION",
        paddingBottom("12px"),
        div(flex("1")),
        child.text <-- questionVar.signal.map { string =>
          s"${string.length} / 240"
        }
      ),
      textArea(
        display.block,
        height("100px"),
        disabled <-- isAskingVar.signal.map(_.isEmpty),
        inContext { el =>
          isAskingVar.signal.changes.filter(_.isDefined) --> { _ =>
            el.ref.focus()
          }
        },
        onKeyDown.filter(k => k.key == "Enter" && k.metaKey).map(_.key) --> { _ => submitQuestion() },
        onKeyDown.map(_.key).filter(_ == "Escape") --> { _ => isAskingVar.set(None) },
        controlled(
          value <-- questionVar,
          onInput.mapToValue.map(_.take(240)) --> questionVar
        )
      ),
      div(
        display.flex,
        button("CANCEL", cls("cancel"), onClick --> { _ => isAskingVar.set(None) }),
        div(width("24px")),
        button("SEND", onClick --> { _ => submitQuestion() })
      )
    )
  }

  private def ActiveQuestion =
    div(
      panelStyles(questionStateVar.signal.map(_.activeQuestion.isDefined)),
      textAlign.left,
      div(
        div(
          fontStyle.italic,
          fontSize.medium,
          opacity(0.7),
          "QUESTION"
        ),
        div(
          padding("12px 0"),
          fontWeight.bold,
          child.maybe <-- questionStateVar.signal.map(_.activeQuestion.map(_.question))
        )
      )
    )

  def view: Div = div(
    ws.connect,
    VoteModule.voteBus.events.debounce(500) --> { vote =>
      println(s"SENDING VOTE $vote ")
      ws.sendOne(vote)
    },
    ws.received --> { command =>
      println(s"RECEIVED COMMAND: $command")
      command match {
        case ServerCommand.SendSlideState(slideState) =>
          slideStateVar.set(slideState)
        case ServerCommand.SendQuestionState(questionState) =>
          questionStateVar.set(questionState)
        case ServerCommand.SendVotes(votes) =>
          voteStateVar.update(_.processUpdates(votes.filterNot(v => userIdVar.now().contains(v.id))))
        case ServerCommand.SendUserId(id) =>
          userIdVar.set(Some(id))
        case ServerCommand.SendPopulationStats(populationStats) =>
          populationStatsVar.set(populationStats)
      }
    },
    BottomPanel,
    textAlign.center,
    div(
      position.relative,
      position.fixed,
      top("0"),
      bottom("0"),
      left("0"),
      right("0"),
      background("#111"),
      cls <-- isAskingVar.signal
        .map(_.isDefined)
        .combineWithFn(questionStateVar.signal.map(_.activeQuestion.isDefined))(_ || _)
        .map { if (_) "slide-app-shrink" else "slide-app" },
      pre(
        "Zymposium — ",
        child.text <-- populationStatsVar.signal.map(_.connectedUsers.toString),
        onDblClick --> { _ =>
          val state = slideStateVar.now()
          isAskingVar.update {
            case Some(_) => None
            case None    => Some(SlideIndex(state.slideIndex, state.stepIndex))
          }
        }
      ),
      renderSlides
    )
  )

  val $slideState: Signal[SlideState] = slideStateVar.signal.combineWithFn(slideIndexOverride) {
    case (ss, Some(SlideIndex(slide, step))) =>
      ss.copy(slide, ss.slideStepMap.updated(slide, step))
    case (ss, None) => ss
  }

  def renderSlides: Div = div(
    position.relative,
    Slide.exampleSlides.zipWithIndex.map { case (slide, index) =>
      val $step  = $slideState.map(_.stepForSlide(index))
      val $delta = $slideState.map(_.slideIndex - index)

      div(
        position.absolute,
        textAlign.center,
        width("100%"),
        div(
          margin("0 auto"),
          width("80%"),
          padding("24px"),
          position.relative,
          cls <-- $delta.map { d =>
            if (d == 0) "slide-current slide"
            else if (d > 0) "slide-next slide"
            else "slide-previous slide"
          },
          slide.render($step)
        )
      )
    }
  )
}

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

  private def VoteView(topic: VoteState.Topic, vote: VoteState.Vote) = {
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
        width <-- $votePercentage.map(d => s"${d * 100}%"),
        bottom("0"),
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
