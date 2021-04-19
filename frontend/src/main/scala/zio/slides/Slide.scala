package zio.slides

import animus._
import com.raquo.laminar.api.L._
import zio.slides.VoteModule.VotesView
import components.FadeInWords
import zio.slides.VoteState.Topic

import scala.util.Random

trait Slide {
  def render($step: Signal[Int]): HtmlElement
}

object Components {
  def title(modifiers: Modifier[HtmlElement]*) =
    h1(modifiers)

  def slideIn($isActive: Signal[Boolean])(component: => Modifier[HtmlElement]): Modifier[HtmlElement] =
    children <-- $isActive.splitOneTransition(identity) { (_, b, _, transition) =>
      if (b) {
        div(
          div(component),
          Transitions.heightDynamic(transition.$isActive)
        )
      } else {
        div()
      }
    }
}

object Slide {

  object Slide_1 extends Slide {
    override def render($step: Signal[Int]): HtmlElement =
      div(
        Components.title(
          justifyContent.center,
//          display.flex,
          children <-- $step.map(_ > 1)
            .map { if (_) "Redeeming" else "Building" }
            .splitOneTransition(identity) { (_, word, _, transition) =>
              div(
                display.inlineFlex,
                overflow.hidden,
                div(word),
                Transitions.widthDynamic(transition.$isActive)
//                transition.width
              )
            },
          s"${nbsp}a ZIO App"
        ),
        FadeInWords("🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞", $step.map(_ > 2)),
        p(
          FadeInWords("Today, we're going to build an interactive slide app with ZIO.", $step.map(_ > 0))
        ),
        FadeInWords("🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞", $step.map(_ > 2)),
        p(
          FadeInWords("And it's actually going to work.", $step.map(_ > 1))
        ),
        FadeInWords("🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞", $step.map(_ > 2))
      )
  }

  object Slide_2 extends Slide {
    override def render($step: Signal[Int]): HtmlElement = {

      def slideSim($active: Signal[Boolean], $step: Signal[Int]): Div = div(
        width("120px"),
        height("90px"),
        background("#444"),
        borderRadius("4px"),
        padding("12px"),
        opacity <-- $active.map { if (_) 1.0 else 0.5 }.spring,
        transform <-- $active.map { if (_) 1.0 else 0.95 }.spring.map { s => s"scale($s)" },
        Components.slideIn($step.map(_ >= 0)) {
          div(
            height(s"${Random.nextInt(15) + 10}px"),
            background("#888"),
            borderRadius("4px"),
            marginBottom("8px")
          )
        },
        Components.slideIn($step.map(_ >= 1)) {
          div(
            height(s"${Random.nextInt(20) + 10}px"),
            background("#888"),
            borderRadius("4px"),
            marginBottom("8px")
          )
        },
        Components.slideIn($step.map(_ >= 2)) {
          div(
            height(s"${Random.nextInt(20) + 10}px"),
            background("#888"),
            marginBottom("8px"),
            borderRadius("4px")
          )
        }
      )

      div(
        Components.title("Meta"),
        p(
          FadeInWords("This is a slideshow.", $step.map(_ > 0))
        ),
        p(
          FadeInWords("A Slide Index is being broadcast via WebSockets.", $step.map(_ > 1))
        ),
        div(
          marginBottom("24px"),
          display.flex,
          justifyContent.center,
          alignItems.center,
          div(
            div(
              fontSize.small,
              opacity(0.8),
              "SLIDE"
            ),
            AnimatedCount($step.map(_ / 3 + 1))
          ),
          div(width("24px")),
          div(
            div(
              fontSize.small,
              opacity(0.8),
              "STEP"
            ),
            AnimatedCount($step.map(_ % 3 + 1))
          )
        ),
        div(
          display.flex,
          justifyContent.center,
          alignItems.center,
          slideSim($step.map(Set(0, 1, 2)), $step),
          div(width("24px")),
          slideSim($step.map(Set(3, 4, 5)), $step.map(_ - 3)),
          div(width("24px")),
          slideSim($step.map(Set(6, 7, 8)), $step.map(_ - 6))
        ),
        p(
          FadeInWords("Everything is written in Scala.", $step.map(_ > 3))
        ),
        p(
          FadeInWords("I love Scala 😭", $step.map(_ > 5))
        )
      )
    }

  }

  object Slide_3 extends Slide {
    override def render($step: Signal[Int]): HtmlElement =
      div(
        h1("A Special Guest!"),
        h3(
          FadeInWords("Tushar Mathur", $step.map(_ > 0))
        )
      )
  }

  object Slide_4 extends Slide {
    override def render($step: Signal[Int]): HtmlElement =
      div(
        h1("POLL / DDOS"),
        p(
          FadeInWords("What's your preferred Scala web server?", $step.map(_ > 0))
        ),
        Components.slideIn($step.map(_ > 1)) {
          VotesView(
            Topic("Web Frameworks"),
            List(
              "Akka HTTP",
              "Cask",
              "Finch",
              "Play",
              "http4s",
              "zio-http"
            ),
            $step.map(_ <= 2)
          )
        }
      )
  }

  object Slide_5 extends Slide {
    override def render($step: Signal[Int]): HtmlElement =
      div(
        h1("Today's Topics"),
        p("Hover over a topic to vote!"),
        VotesView()
      )
  }

  val exampleSlides: Vector[Slide] = Vector(Slide_1, Slide_2, Slide_3, Slide_4)
}
