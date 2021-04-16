import com.raquo.laminar.api.L._
import animus._

package object components {
  def FadeInWords(string: String, delay: Int = 0): Modifier[HtmlElement] = {
    string.split(" ").zipWithIndex.toList.map { case (word, idx) =>
      val $opacity = Animation.from(0).wait(delay + 150 * idx).to(1).run
      div(
        word + nbsp,
        lineHeight("1.5"),
        display.inlineFlex,
        opacity <-- $opacity,
        position.relative,
        Transitions.height($opacity.map(_ > 0)),
        onMountBind { el =>
          top <-- Animation.from(el.thisNode.ref.scrollHeight).wait(delay + 150 * idx).to(0).run.px
        }
      )
    }
  }

}
