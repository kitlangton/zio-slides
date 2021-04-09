package zio.slides

import com.raquo.laminar.api.L._
import zio.slides.Slides.VotesView

trait Slide {
  def render($step: Signal[Int]): HtmlElement
}

object Slide {

  object Slide_1 extends Slide {
    override def render($step: Signal[Int]): HtmlElement =
      div(
        h2("Building a ZIO App"),
        p(
          cls <-- $step.map(_ >= 1).map {
            if (_) "visible" else "hidden"
          },
          "Today we're going to build a ZIO backend—"
        ),
        p(
          cls <-- $step.map(_ >= 2).map {
            if (_) "visible" else "hidden"
          },
          "—for an interactive slideshow app."
        )
      )
  }

  object Slide_2 extends Slide {
    override def render($step: Signal[Int]): HtmlElement = {
      div(
        h1("Slide 2"),
        p("Lorem ipsum dolor amet."),
        p(
          child.text <-- $step.map { step => s"STEP: $step" }
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
