package app.util.redis4cats.codecs

import dev.profunktor.redis4cats.codecs.Codecs
import dev.profunktor.redis4cats.codecs.splits.SplitEpi
import dev.profunktor.redis4cats.data.RedisCodec
import io.circe.Error

object CirceCodecs {

  // base codec
  // RedisCodec.Utf8: RedisCodec[String, String]

  // def derive[K, V1, V2](
  //     baseCodec: RedisCodec[K, V1],
  //     epi: SplitEpi[V1, V2]
  // ): RedisCodec[K, V2]

  // def derive[K1, K2, V1, V2](
  //     baseCodec: RedisCodec[K1, V1],
  //     epiKeys: SplitEpi[K1, K2],
  //     epiValues: SplitEpi[V1, V2]
  // ): RedisCodec[K2, V2]

  // final case class SplitEpi[A, B](
  //     get: A => B,
  //     reverseGet: B => A
  // )

  def derive[K, V](baseStringCodec: RedisCodec[String, String])(implicit
      epiKeys: SplitEpi[String, Either[Error, K]],
      epiValues: SplitEpi[String, Either[Error, V]]
  ): RedisCodec[Either[Error, K], Either[Error, V]] =
    Codecs.derive(baseStringCodec, epiKeys, epiValues)
}
