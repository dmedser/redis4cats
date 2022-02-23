package app

import cats.data.{NonEmptyList => Nel}
import cats.effect.kernel.Resource
import cats.effect.std.Console
import cats.effect.syntax.spawn._
import cats.effect.{Async, IO, IOApp, Temporal}
import dev.profunktor.redis4cats.connection.{RedisClient, RedisURI}
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.Stdout._
import dev.profunktor.redis4cats.{Redis, RedisCommands}

import scala.concurrent.duration._

object ListBlockingCommands extends IOApp.Simple {

  def run: IO[Unit] = program[IO]

  type K = String
  type V = String

  type Redis[F[_]] = RedisCommands[F, K, V]

  private def resource[F[_] : Async]: Resource[F, (Redis[F], Redis[F])] =
    for {
      uri     <- Resource.eval(RedisURI.make[F](redisUri))
      client0 <- RedisClient[F].fromUri(uri)
      client1 <- RedisClient[F].fromUri(uri)
      redis0  <- Redis[F].fromClient(client0, RedisCodec.Utf8)
      redis1  <- Redis[F].fromClient(client1, RedisCodec.Utf8)
    } yield redis0 -> redis1

  private def program[F[_] : Async : Console]: F[Unit] = {
    import cats.syntax.apply._
    import cats.syntax.flatMap._
    import cats.syntax.functor._

    val console: Console[F] = Console[F]

    def lRange(r: Redis[F])(k: K, s: Long, e: Long): F[List[V]] =
      console.println(s"lRange(key = '$k', start = $s, end = $e)") *>
        r
          .lRange(k, s, e)
          .flatTap { vs =>
            console.println {
              s"Found ${vs.size} value(s) by key '$k' within [$s, $e] indexes: " +
                vs.mkString("'", "', '", "'")
            }
          }

    def blPop(r: Redis[F])(t: Duration, ks: Nel[K]): F[Option[(K, V)]] = {
      val ksStr = ks.toList.mkString("'", "', '", "'")
      console.println(s"blPop(timeout = $t, keys = $ksStr)") *>
        r.blPop(t, ks)
          .flatTap {
            case Some((k, v)) => console.println(s"Popped value '$v' by key '$k'")
            case None         => console.errorln(s"No element popped during the timeout $t from any of keys: $ksStr")
          }
    }

    def brPopLPush(r: Redis[F])(t: Duration, s: K, d: K): F[Option[V]] =
      console.println(s"brPopLPush(timeout = $t, source = $s, destination = $d)") *>
        r.brPopLPush(t, s, d)
          .flatTap {
            case Some(v) => console.println(s"Last value '$v' is removed from '$s' and prepended to '$d'")
            case None    => console.errorln(s"Source key '$s' or (and) destination key '$d' not found during timeout $t")
          }

    def rPush(r: Redis[F])(k: K, vs: V*): F[Long] = {
      val vsStr = vs.mkString("'", "', '", "'")
      console.println(s"rPush(key = '$k', values = $vsStr)") *>
        r.rPush(k, vs: _*)
          .flatTap(n => console.println(s"Current number of elements by key '$k': $n"))
    }

    def sleep(d: FiniteDuration): F[Unit] =
      console.println(s"sleep(duration = $d)") *> Temporal[F].sleep(d)

    def del(r: Redis[F])(ks: K*): F[Long] = {
      val ksStr = ks.mkString("'", "', '", "'")
      console.println(s"del(keys = $ksStr)") *>
        r.del(ks: _*).flatTap(n => console.println(s"Deleted $n key(s): " + ksStr))
    }

    resource.use { case (r0, r1) =>
      for {
        _ <- console.println("********** List blocking commands test **********")

        _ <- console.println("********** Test 0 **********")

        (k01, k02) = ("k01", "k02")
        (v01, v02) = ("v01", "v02")

        f <- blPop(r0)(10.seconds, Nel.of(k01, k02)).start
        _ <- sleep(2.seconds)
        _ <- rPush(r1)(k02, v02)
        _ <- f.join
        _ <- del(r1)(k02)

        _ <- console.println("********** Test 1 **********")

        (k11, k12) = ("ak", "bk")
        (v11, v12) = ("av", "bv")

        _ <- rPush(r0)(k12, v12)
        f <- brPopLPush(r0)(10.seconds, k11, k12).start
        _ <- sleep(2.seconds)
        _ <- rPush(r1)(k11, v11)
        _ <- lRange(r1)(k11, 0, -1)
        _ <- lRange(r1)(k12, 0, -1)
        _ <- f.join
        _ <- del(r1)(k11)
        _ <- del(r0)(k12)

      } yield ()
    }
  }
}
