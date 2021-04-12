package zio.slides

import com.raquo.laminar.api.L._

object Styles {
  def panelStyles(isVisible: Signal[Boolean] = Val(false)) =
    Seq(
      cls <-- isVisible.map { if (_) "panel-visible" else "panel-hidden" },
      height <-- isVisible.map { if (_) "auto" else "0px" },
      padding <-- isVisible.map { if (_) "24px" else "0px" },
      background("black"),
      borderTop("1px solid #333"),
      borderTopWidth <-- isVisible.map { if (_) "1px" else "0px" }
    )
}
