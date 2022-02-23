package app.model

import io.circe.generic.JsonCodec

@JsonCodec
final case class Id(value: Long) extends AnyVal
