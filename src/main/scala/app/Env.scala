package app

import cats.FlatMap
import cats.effect.Temporal
import cats.syntax.apply._
import cats.syntax.flatMap._
import dev.profunktor.redis4cats.RedisCommands
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.FiniteDuration

trait Env[F[_]] {

  val redis: RedisCommands[F, String, String]

  implicit val log: Logger[F]

  def exists(ks: String*)(implicit F: FlatMap[F]): F[Boolean] = {
    val ksStr0 = s"${ks.size} key(s): "
    val ksStr1 = ks.mkString("'", "', '", "'")
    val ksStr  = ksStr0 + ksStr1

    log.info(s"exists(keys = $ksStr1)") *>
      redis
        .exists(ks: _*)
        .flatTap {
          case true  => log.info(s"All of $ksStr found")
          case false => log.warn(s"Not all of $ksStr found")
        }
  }

  def del(ks: String*)(implicit F: FlatMap[F]): F[Long] = {
    val ksStr = ks.mkString("'", "', '", "'")
    log.info(s"del(keys = $ksStr)") *>
      redis
        .del(ks: _*)
        .flatTap(n => log.info(s"Deleted $n key(s): " + ksStr))
  }

  def sleep(d: FiniteDuration)(implicit F: Temporal[F]): F[Unit] =
    log.info(s"sleep(duration = $d)") *> Temporal[F].sleep(d)
}
