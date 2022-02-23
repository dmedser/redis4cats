package app

import cats.effect._
import cats.effect.syntax.resource._
import dev.profunktor.redis4cats.log4cats._
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait App extends IOApp.Simple {

  private def resource[F[_] : Async]: Resource[F, Logger[F] => Env[F]] =
    for {
      implicit0(log: Logger[F]) <- Slf4jLogger.create[F].toResource // TODO NoOp?
      // Single Node:
      //
      // 1. def simple[K, V](uri: String, codec: RedisCodec[K, V]): Resource[F, RedisCommands[F, K, V]]
      //
      // 2. def withOptions[K, V](
      //        uri: String,
      //        opts: ClientOptions,
      //        codec: RedisCodec[K, V]
      //    ): Resource[F, RedisCommands[F, K, V]]
      //
      // 3. def utf8(uri: String): Resource[F, RedisCommands[F, String, String]]
      //
      // 4. def fromClient[K, V](
      //        client: RedisClient,
      //        codec: RedisCodec[K, V]
      //    ): Resource[F, RedisCommands[F, K, V]]
      //
      // redis <- Redis[F].simple(uri = redisUri, codec = CirceCodecs.derive[Id, Person](RedisCodec.Utf8))
      cmd <- Redis[F].utf8(uri = redisUri)
    } yield (logger: Logger[F]) =>
      new Env[F] {
        val redis: RedisCommands[F, String, String] = cmd
        implicit val log: Logger[F]                 = logger
      }

  protected def program[F[_] : Async]: (Logger[F] => Env[F]) => F[Unit]

  def run: IO[Unit] = resource[IO].use(program)
}
