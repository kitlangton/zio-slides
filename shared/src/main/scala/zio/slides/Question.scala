package zio.slides

import zio.json.{DeriveJsonCodec, JsonCodec, JsonDecoder, JsonEncoder}

import java.util.UUID

case class SlideIndex(slide: Int, step: Int)

object SlideIndex {
  implicit val codec: JsonCodec[SlideIndex] = DeriveJsonCodec.gen[SlideIndex]
}

case class Question private (id: UUID, question: String, slideIndex: SlideIndex)

object Question {

  def apply(question: String, slideIndex: SlideIndex): Question =
    new Question(UUID.randomUUID(), question, slideIndex)

  implicit val uuidCodec                  = JsonCodec(JsonEncoder.uuid, JsonDecoder.uuid)
  implicit val codec: JsonCodec[Question] = DeriveJsonCodec.gen[Question]
}
