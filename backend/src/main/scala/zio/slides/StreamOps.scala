package zio.slides

import zio.ZIO
import zio.interop.catz._
import zio.stream.ZStream

object StreamOps {
  implicit class ZStreamSyntax[R, E, A](private val stream: ZStream[R, E, A]) extends AnyVal {

    /** Convert a [[zio.stream.ZStream]] into an [[fs2.Stream]]. */
    def toFs2Stmream: fs2.Stream[ZIO[R, E, *], A] =
      fs2.Stream.resource(stream.process.toResourceZIO).flatMap { pull =>
        fs2.Stream.repeatEval(pull.optional).unNoneTerminate.flatMap { chunk =>
          fs2.Stream.chunk(fs2.Chunk.indexedSeq(chunk))
        }
      }
  }
}
