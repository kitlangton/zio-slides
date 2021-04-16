package zio.slides

import animus._
import com.raquo.laminar.api.L._
import zio.slides.VoteModule.VotesView
import components.FadeInWords

trait Slide {
  def render($step: Signal[Int]): HtmlElement
}

object Components {
  def title(modifiers: Modifier[HtmlElement]*) =
    h1(modifiers)

  def fadeIn($isActive: Signal[Boolean])(component: => Modifier[HtmlElement]): Modifier[HtmlElement] =
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
                div(word),
                Transitions.widthDynamic(transition.$isActive)
//                transition.width
              )
            },
          s"${nbsp}a ZIO App"
        ),
        Components.fadeIn($step.map(_ > 2)) {
          FadeInWords("🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞")
        },
        Components.fadeIn($step.map(_ > 0)) {
          p(
            FadeInWords("Today, we're going to build an interactive slide app with ZIO.")
          )
        },
        Components.fadeIn($step.map(_ > 2)) {
          FadeInWords("🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞")
        },
        Components.fadeIn($step.map(_ > 1)) {
          p(
            FadeInWords("And it's actually going to work.")
          )
        },
        Components.fadeIn($step.map(_ > 2)) {
          FadeInWords("🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞 🤞")
        }
      )
  }

  object Slide_2 extends Slide {
    override def render($step: Signal[Int]): HtmlElement = {
      div(
        Components.title("Slide 2"),
        Components.fadeIn($step.map(_ > 0)) {
          div(
            FadeInWords("Lorem ipsum dolor amet!"),
            marginBottom("18px")
          )
        },
        div(
          textAlign.center,
          AnimatedCount($step)
        )
      )
    }

  }

  object Slide_3 extends Slide {
    override def render($step: Signal[Int]): HtmlElement =
      div(
        h1("Slide 3"),
        p("Fantastic stuff!"),
        p(
          child.text <-- $step.map { step => s"STEP: $step" }
        )
      )
  }

  object Slide_4 extends Slide {
    override def render($step: Signal[Int]): HtmlElement =
      div(
        h1("Today's Topics"),
        p("Hover over a topic to vote!"),
        VotesView()
      )
  }

  val exampleSlides: Vector[Slide] = Vector(Slide_1, Slide_2, Slide_3, Slide_4)
}
