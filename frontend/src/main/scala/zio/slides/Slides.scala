package zio.slides

import _root_.boopickle.Default._
import _root_.zio.slides.State._
import _root_.zio.slides.Styles._
import animus._
import com.raquo.laminar.api.L._
import io.laminext.websocket._
import io.laminext.websocket.boopickle.WebSocketReceiveBuilderBooPickleOps

import scala.concurrent.duration.DurationInt

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
      .pickle[ServerCommand, UserCommand]
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

  lazy val $connectionStatus = ws.isConnected
    .combineWithFn(ws.isConnecting) {
      case (true, _) => "CONNECTED"
      case (_, true) => "CONNECTING"
      case _         => "OFFLINE"
    }

  def view: Div = {
    div(
      ws.connect,
      ws.connected --> { _ =>
        ws.sendOne(UserCommand.Subscribe)
      },
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
          .map {
            if (_) "slide-app-shrink" else "slide-app"
          },
        div(
          margin("20px"),
          display.flex,
          flexDirection.column,
          height("40px"),
          //        justifyContent.spaceBetween,
          alignItems.flexEnd,
          div(
            fontSize("16px"),
            lineHeight("1.5"),
            div(
              "ZYMPOSIUM"
            ),
            onDblClick --> { _ =>
              val state = slideStateVar.now()
              isAskingVar.update {
                case Some(_) => None
                case None    => Some(SlideIndex(state.slideIndex, state.stepIndex))
              }
            }
          ),
          div(
            fontSize("14px"),
            opacity(0.7),
            div(
              lineHeight("1.5"),
              display.flex,
              children <-- $connectionStatus.splitOneTransition(identity) { (_, string, _, transition) =>
                div(string, transition.width, transition.opacity)
              },
              overflowY.hidden,
              height <-- EventStream
                .merge(
                  $connectionStatus.changes.debounce(5000).mapTo(false),
                  $connectionStatus.changes.mapTo(true)
                )
                .toSignal(false)
                .map { if (_) 20.0 else 0.0 }
                .spring
                .px
            )
          ),
          div(
            opacity <-- Animation.from(0).wait(1000).to(1).run,
            lineHeight("1.5"),
            display.flex,
            height("40px"),
            fontSize("14px"),
            div(s"POP.${nbsp}", opacity(0.7)),
            AnimatedCount(populationStatsVar.signal.map(_.connectedUsers))
          )
        ),
        renderSlides
      )
    )
  }

  val $slideState: Signal[SlideState] = slideStateVar.signal.combineWithFn(slideIndexOverride) {
    case (ss, Some(SlideIndex(slide, step))) => ss.copy(slide, ss.slideStepMap.updated(slide, step))
    case (ss, None)                          => ss
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
