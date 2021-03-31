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

  def WebsocketStatus: Div = div(
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

  def QuestionPanel: Div = {
    val isAsking    = Var(Option.empty[SlideIndex])
    val questionVar = Var("")

    div(
      "QUESTIONS",
      position.fixed,
      bottom("0"),
      left("0"),
      right("0"),
      windowEvents.onKeyDown.filter(k => k.key == "q" && k.ctrlKey) --> { _ =>
        val state = slideStateVar.now()
        isAsking.update {
          case Some(_) => None
          case None    => Some(SlideIndex(state.slideIndex, state.stepIndex))
        }
      },
      border("1px solid #333"),
      padding("12px"),
      questionStateVar.signal --> { state =>
        val question = state.questions.find(q => state.activeQuestion.contains(q.id))
        question match {
          case Some(question) => slideIndexOverride.set(Some(question.slideIndex))
          case None           => slideIndexOverride.set(None)
        }
      },
      child.maybe <-- isAsking.signal.split(_ => 1) { (_, index, _) =>
        div(
          input(
            controlled(
              value <-- questionVar,
              onInput.mapToValue --> questionVar
            )
          ),
          button(
            "SEND",
            onClick --> { _ =>
              ws.sendOne(UserCommand.AskQuestion(questionVar.now(), index))
              isAsking.set(None)
              questionVar.set("")
            }
          )
        )
      },
      div(
        children <-- questionStateVar.signal.map(_.questions).split(_.id) { (id, question, _) =>
          div(
            color <-- questionStateVar.signal.map(qs => if (qs.activeQuestion.contains(id)) "green" else "white"),
            question.question,
            nbsp,
            span(question.slideIndex.show, opacity(0.5), fontStyle.italic, fontSize.medium),
            onClick --> { _ =>
              ws.sendOne(AdminCommand.ToggleQuestion(id))
            }
          )
        }
      )
    )
  }

  def view: Div = div(
    ws.connect,
    ws.received --> { command =>
      command match {
        case ServerCommand.SendSlideState(slideState) =>
          slideStateVar.set(slideState)
        case ServerCommand.SendAllQuestions(questions) =>
          questionStateVar.update(_.copy(questions = questions))
        case ServerCommand.SendActiveQuestion(activeQuestion) =>
          questionStateVar.update(_.copy(activeQuestion = activeQuestion))
      }
    },
    WebsocketStatus,
    QuestionPanel,
    textAlign.center,
    pre("zio-slides"),
    windowEvents.onKeyDown.map(_.key) --> {
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
    },
    renderSlides
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
