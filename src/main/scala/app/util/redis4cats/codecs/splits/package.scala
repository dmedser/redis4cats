package app.util.redis4cats.codecs

import cats.syntax.option._
import dev.profunktor.redis4cats.codecs.splits.SplitEpi
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Error}

package object splits {
  implicit def circeSplitEpi[A : Encoder : Decoder]: SplitEpi[String, Either[Error, A]] =
    SplitEpi(io.circe.parser.decode[A], _.toOption.map(_.asJson.noSpaces).orEmpty)
}
