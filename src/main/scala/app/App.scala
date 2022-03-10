package app

import cats.effect.syntax.resource._
import cats.effect.{Async, IO, IOApp, Resource}
import dev.profunktor.redis4cats.log4cats._
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

sealed trait App extends IOApp.Simple {

  protected def resource[F[_] : Async]: Resource[F, Logger[F] => Env[F]]

  protected def program[F[_] : Async]: (Logger[F] => Env[F]) => F[Unit]

  def run: IO[Unit] = resource[IO].use(program)
}

object App {

  trait SingleNode extends App {
    protected def resource[F[_] : Async]: Resource[F, Logger[F] => Env[F]] =
      for {
        implicit0(log: Logger[F]) <- Slf4jLogger.create[F].toResource
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
  }

  trait Cluster extends App {
    protected def resource[F[_] : Async]: Resource[F, Logger[F] => Env[F]] =
      for {
        implicit0(log: Logger[F]) <- Slf4jLogger.create[F].toResource
        // Cluster:
        //
        // 1. def cluster[K, V](
        //        codec: RedisCodec[K, V],
        //        uris: String*
        //    )(readFrom: Option[JReadFrom] = None): Resource[F, RedisCommands[F, K, V]]
        //
        // 2. def clusterUtf8(
        //        uris: String*
        //    )(readFrom: Option[JReadFrom] = None): Resource[F, RedisCommands[F, String, String]]
        //
        // 3. def fromClusterClient[K, V](
        //        clusterClient: RedisClusterClient,
        //        codec: RedisCodec[K, V]
        //    )(readFrom: Option[JReadFrom] = None): Resource[F, RedisCommands[F, K, V]]
        //
        // 4. def fromClusterClientByNode[K, V](
        //        clusterClient: RedisClusterClient,
        //        codec: RedisCodec[K, V],
        //        nodeId: NodeId
        //    )(readFrom: Option[JReadFrom] = None): Resource[F, RedisCommands[F, K, V]]
        cmd <- Redis[F].clusterUtf8(uris = (7000 to 7005).map(port => redisUri("127.0.0.1", port)): _*)()
      } yield (logger: Logger[F]) =>
        new Env[F] {
          val redis: RedisCommands[F, String, String] = cmd
          implicit val log: Logger[F]                 = logger
        }
  }
}
