package zio.slides

import com.raquo.laminar.api.L._
import io.laminext.websocket._
import io.laminext.websocket.zio._

object Slides {
  val ws: WebSocket[ServerCommand, AppCommand] =
    WebSocket
      .url("ws://localhost:8088/ws")
      .json[ServerCommand, AppCommand]
      .build(reconnectRetries = Int.MaxValue)

  val slideIndexOverride: Var[Option[SlideIndex]] = Var(None)
  val slideStateVar: Var[SlideState]              = Var(SlideState.empty)
  val questionStateVar: Var[QuestionState]        = Var(QuestionState.empty)
  val isAskingVar                                 = Var(Option.empty[SlideIndex])
  val isAdminVar                                  = Var(true)

  def WebSocketStatus: Div = div(
    border("1px solid #333"),
    padding("12px"),
    child.text <-- ws.isConnecting.map { if (_) "CONNECTING" else "" },
    child.text <-- ws.isConnected.map { if (_) "CONNECTED" else "" },
    div(
      child.text <-- ws.events.map {
        case WebSocketEvent.Connected(ws)             => "CONNECT"
        case WebSocketEvent.Closed(ws, willReconnect) => "CLOSED"
        case WebSocketEvent.Error(error)              => "ERROR"
        case WebSocketEvent.Received(message)         => "RECEIVED"
      }
    )
  )

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
      AdminPanel
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
        button(
          "CANCEL",
          cls("cancel"),
          onClick --> { _ => isAskingVar.set(None) }
        ),
        div(width("24px")),
        button(
          "SEND",
          onClick --> { _ => submitQuestion() }
        )
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

  private def panelStyles(isVisible: Signal[Boolean] = Val(false)) =
    Seq(
      cls <-- isVisible.map { if (_) "panel-visible" else "panel-hidden" },
      height <-- isVisible.map { if (_) "auto" else "0px" },
      padding <-- isVisible.map { if (_) "24px" else "0px" },
      background("black"),
      borderTop("1px solid #333"),
      borderTopWidth <-- isVisible.map { if (_) "1px" else "0px" }
    )

  private def AdminPanel: Div =
    div(
      display <-- isAdminVar.signal.map(if (_) "block" else "none"),
      AllQuestions
    )

  private def AllQuestions: Div =
    div(
      panelStyles(isAdminVar.signal),
      children <-- questionStateVar.signal.map(_.questions).split(_.id) { (id, question, _) =>
        div(
          color <-- questionStateVar.signal.map(qs => if (qs.activeQuestionId.contains(id)) "green" else "white"),
          textAlign.left,
          fontSize.medium,
          cursor.pointer,
          margin("8px 0px"),
          question.question,
          nbsp,
          span(question.slideIndex.show, opacity(0.5), fontStyle.italic, fontSize.medium),
          onClick --> { _ => ws.sendOne(AdminCommand.ToggleQuestion(id)) }
        )
      }
    )

  val voteStateVar = Var(VoteState.empty)

  def view = {
    val topic = VoteState.Topic("Favorite Colors")

    val options = List(
      "Red",
      "Blue",
      "Green"
    )

    div(
      child.text <-- userId.signal.map(_.toString),
      ws.connect,
      voteBus.events --> { vote =>
//        voteBus.events.debounce(500) --> { vote =>
        println(s"SENDING VOTE $vote ")
        ws.sendOne(vote)
      },
      ws.received --> { command =>
        println(s"RECEVIED $command")
        command match {
          case ServerCommand.SendSlideState(slideState) =>
            slideStateVar.set(slideState)
          case ServerCommand.SendAllQuestions(questions) =>
            questionStateVar.update(_.copy(questions = questions))
          case ServerCommand.SendActiveQuestion(activeQuestion) =>
            questionStateVar.update(_.copy(activeQuestionId = activeQuestion))
          case ServerCommand.SendVotes(votes) =>
            voteStateVar.update(_.processUpdates(votes))
          case ServerCommand.SendUserId(id) =>
            userId.set(id)
        }
      },
      div("VOTES"),
//      child.text <-- voteStateVar.signal.map(_.toString),
      options.map { string =>
        VoteView(topic, VoteState.Vote(string))
      }
    )
  }

  lazy val userId = Var(VoteState.UserId("not-connected"))

  lazy val voteBus: EventBus[UserCommand.SendVote] = new EventBus

  private def VoteView(topic: VoteState.Topic, vote: VoteState.Vote) = {
    val $totalVotes = voteStateVar.signal.map(_.voteTotals(topic).getOrElse(vote, 0))
    div(
      vote.string,
      " ",
      child.text <-- $totalVotes,
      onMouseEnter --> { _ =>
        voteBus.emit(UserCommand.SendVote(topic, vote))
        voteStateVar.update(_.processUpdate(VoteState.CastVoteId(userId.now(), topic, vote)))
      }
    )
  }

  def view2: Div = div(
    ws.connect,
    ws.received --> { command =>
      command match {
        case ServerCommand.SendSlideState(slideState) =>
          slideStateVar.set(slideState)
        case ServerCommand.SendAllQuestions(questions) =>
          questionStateVar.update(_.copy(questions = questions))
        case ServerCommand.SendActiveQuestion(activeQuestion) =>
          questionStateVar.update(_.copy(activeQuestionId = activeQuestion))
        case ServerCommand.SendVotes(votes) =>
          voteStateVar.update(_.processUpdates(votes))
        case ServerCommand.SendUserId(id) =>
          userId.set(id)
      }
    },
    WebSocketStatus,
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
      pre("zymposium"),
      windowEvents.onKeyDown.map(_.key) --> {
        case "‡" =>
          isAdminVar.update(!_)
        case "ArrowRight" =>
          slideStateVar.update(_.nextSlide)
          ws.sendOne(AdminCommand.NextSlide)
        case "ArrowLeft" =>
          slideStateVar.update(_.prevSlide)
          ws.sendOne(AdminCommand.PrevSlide)
        case "ArrowDown" =>
          slideStateVar.update(_.nextStep)
          ws.sendOne(AdminCommand.NextStep)
        case "ArrowUp" =>
          slideStateVar.update(_.prevStep)
          ws.sendOne(AdminCommand.PrevStep)
        case _ => ()
      },
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
