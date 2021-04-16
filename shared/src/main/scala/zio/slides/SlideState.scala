package zio.slides

case class PopulationStats(connectedUsers: Int) {
  def addOne: PopulationStats =
    copy(connectedUsers = connectedUsers + 1)

  def removeOne: PopulationStats =
    copy(connectedUsers = connectedUsers - 1)
}

case class SlideState(slideIndex: Int, slideStepMap: Map[Int, Int]) {
  def stepIndex: Int = stepForSlide(slideIndex)

  def prevSlide: SlideState = copy(slideIndex = (slideIndex - 1) max 0)
  def nextSlide: SlideState = copy(slideIndex = slideIndex + 1)

  def prevStep: SlideState =
    copy(slideStepMap = slideStepMap.updated(slideIndex, Math.max(0, stepForSlide(slideIndex) - 1)))
  def nextStep: SlideState =
    copy(slideStepMap = slideStepMap.updated(slideIndex, stepForSlide(slideIndex) + 1))

  def stepForSlide(slideIndex0: Int): Int =
    slideStepMap.getOrElse(slideIndex0, 0)
}

object SlideState {
  def empty: SlideState =
    SlideState(0, Map.empty)

  def random: SlideState = {
    val randomSlide = scala.util.Random.nextInt(3)
    val randomStep  = scala.util.Random.nextInt(3)
    SlideState(randomSlide, Map(randomSlide -> randomStep))
  }
}
