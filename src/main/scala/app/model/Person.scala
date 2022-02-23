package app.model

import io.circe.generic.JsonCodec

@JsonCodec
final case class Person(name: String, age: Int)
