package zio.slides

import boopickle.Default._
import com.raquo.laminar.api.L._
import io.laminext.websocket.WebSocket
import io.laminext.websocket.boopickle._
import org.scalajs.dom.window
import zio.slides.State.{questionStateVar, slideStateVar}
import zio.slides.Styles.panelStyles

object Admin {
  lazy val localStoragePassword = scala.util.Try(window.localStorage.getItem("password")).toOption

  val adminWs: WebSocket[ServerCommand, AdminCommand] =
    WebSocket
      .url(Config.webSocketsUrl + s"/admin?password=${localStoragePassword.getOrElse("")}")
      .pickle[ServerCommand, AdminCommand]
      .build(reconnectRetries = 0)

  def AdminPanel: Div =
    div(
      localStoragePassword.map { _ =>
        adminWs.connect
      },
      child.maybe <-- adminWs.isConnected.map {
        Option.when(_) {
          div(
            display <-- adminWs.isConnected.map(if (_) "block" else "none"),
            AllQuestions,
            windowEvents.onKeyDown.map(_.key) --> {
              case "ArrowRight" =>
                slideStateVar.update(_.nextSlide)
                adminWs.sendOne(AdminCommand.NextSlide)
              case "ArrowLeft" =>
                slideStateVar.update(_.prevSlide)
                adminWs.sendOne(AdminCommand.PrevSlide)
              case "ArrowDown" =>
                slideStateVar.update(_.nextStep)
                adminWs.sendOne(AdminCommand.NextStep)
              case "ArrowUp" =>
                slideStateVar.update(_.prevStep)
                adminWs.sendOne(AdminCommand.PrevStep)
              case _ => ()
            }
          )
        }
      }
    )

  private def AllQuestions: Div =
    div(
      panelStyles(Val(true)),
      cls("all-questions"),
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
          onClick --> { _ => adminWs.sendOne(AdminCommand.ToggleQuestion(id)) }
        )
      }
    )
}
