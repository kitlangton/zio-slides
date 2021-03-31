package io.laminext.websocket
import _root_.zio.json._

object zio {

  implicit class WebSocketReceiveBuilderCirceOps(b: WebSocketReceiveBuilder) {
    @inline def json[Receive, Send](implicit
        receiveDecoder: JsonDecoder[Receive],
        sendEncoder: JsonEncoder[Send]
    ): WebSocketBuilder[Receive, Send] =
      new WebSocketBuilder[Receive, Send](
        url = b.url,
        initializer = initialize.text,
        sender = send.text[Send](sendEncoder.encodeJson(_, Some(0)).toString),
        receiver = receive.text[Receive](receiveDecoder.decodeJson(_).left.map(new Error(_)))
      )
  }

}
