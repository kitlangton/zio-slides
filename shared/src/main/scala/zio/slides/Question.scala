package zio.slides

import java.util.UUID

case class SlideIndex(slide: Int, step: Int) {
  def show: String = s"($slide,$step)"
}

case class Question(id: UUID, question: String, slideIndex: SlideIndex)

object Question {
  def apply(question: String, slideIndex: SlideIndex): Question =
    new Question(UUID.randomUUID(), question, slideIndex)
}
