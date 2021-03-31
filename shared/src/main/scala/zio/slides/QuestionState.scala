package zio.slides

import zio.json.{DeriveJsonCodec, JsonCodec, JsonDecoder, JsonEncoder}

import java.util.UUID

case class QuestionState(
    questions: Vector[Question] = Vector.empty,
    activeQuestion: Option[UUID] = None
) {

  def toggleQuestion(uuid: UUID): QuestionState =
    if (activeQuestion.contains(uuid)) copy(activeQuestion = None)
    else copy(activeQuestion = Some(uuid))

  def askQuestion(question: String, slideIndex: SlideIndex): QuestionState =
    copy(questions = questions.appended(Question(question, slideIndex)))
}

object QuestionState {

  def empty: QuestionState = QuestionState()

  implicit val uuidCodec                       = JsonCodec(JsonEncoder.uuid, JsonDecoder.uuid)
  implicit val codec: JsonCodec[QuestionState] = DeriveJsonCodec.gen[QuestionState]
}
