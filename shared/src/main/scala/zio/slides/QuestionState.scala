package zio.slides

import zio.json.{DeriveJsonCodec, JsonCodec, JsonDecoder, JsonEncoder}

import java.util.UUID

case class QuestionState(
    questions: Vector[Question] = Vector.empty,
    activeQuestionId: Option[UUID] = None
) {

  def activeQuestion: Option[Question] = questions.find(q => activeQuestionId.contains(q.id))

  def toggleQuestion(uuid: UUID): QuestionState =
    if (activeQuestionId.contains(uuid)) copy(activeQuestionId = None)
    else copy(activeQuestionId = Some(uuid))

  def askQuestion(question: String, slideIndex: SlideIndex): QuestionState =
    copy(questions = questions.appended(Question(question, slideIndex)))
}

object QuestionState {

  def empty: QuestionState = QuestionState()

  implicit val codec: JsonCodec[QuestionState] = DeriveJsonCodec.gen[QuestionState]
}
