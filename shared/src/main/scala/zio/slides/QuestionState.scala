package zio.slides

import java.util.UUID

case class QuestionState(
    questions: Vector[Question] = Vector.empty,
    activeQuestionId: Option[UUID] = None
) {

  def activeQuestion: Option[Question] = questions.find(q => activeQuestionId.contains(q.id))

  def toggleQuestion(qid: UUID): QuestionState =
    if (activeQuestionId.contains(qid)) copy(activeQuestionId = None)
    else copy(activeQuestionId = Some(qid))

  def askQuestion(question: String, slideIndex: SlideIndex): QuestionState =
    copy(questions = questions.appended(Question(question, slideIndex)))
}

object QuestionState {
  def empty: QuestionState = QuestionState()
}
