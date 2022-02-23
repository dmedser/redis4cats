package app

import cats.effect.Async
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import dev.profunktor.redis4cats.effects.SetArg.Existence._
import dev.profunktor.redis4cats.effects.SetArg.Ttl._
import dev.profunktor.redis4cats.effects.SetArgs
import dev.profunktor.redis4cats.hlist._
import dev.profunktor.redis4cats.log4cats._
import dev.profunktor.redis4cats.pipeline.{PipelineError, RedisPipeline}
import dev.profunktor.redis4cats.transactions._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.TimeoutException
import scala.concurrent.duration._

object StringCommands extends App {

  protected def program[F[_] : Async]: (Logger[F] => Env[F]) => F[Unit] =
    Slf4jLogger.create.map(_) >>= { env =>
      import env._

      def get(k: String): F[Option[String]] =
        log.info(s"get(key = '$k')") *>
          redis
            .get(k)
            .flatTap {
              case Some(v) => log.info(s"Found value '$v' by key '$k'")
              case None    => log.warn(s"Key '$k' not found")
            }

      def strLen(k: String): F[Option[Long]] =
        log.info(s"strLen(key = '$k')") *>
          redis
            .strLen(k)
            .flatTap {
              case Some(l) => log.info(s"Length of value of key '$k': $l")
              case None    => log.warn(s"Key '$k' not found")
            }

      def getRange(k: String, s: Long, e: Long): F[Option[String]] =
        log.info(s"getRange(key = '$k', start = $s, end = $e)") *>
          redis
            .getRange(k, s, e)
            .flatTap {
              case Some(v) => log.info(s"Extracted value '$v' by key '$k' within [$s, $e] indexes")
              case None    => log.warn(s"Key '$k' not found")
            }

      def append(k: String, v: String): F[Unit] =
        log.info(s"append(key = '$k', value = '$v')") *>
          redis.append(k, v) *>
          log.info(s"Value '$v' is appended to value of key '$k'")

      def getSet(k: String, v: String): F[Option[String]] =
        log.info(s"getSet(key = '$k', value = '$v')") *>
          redis
            .getSet(k, v)
            .flatTap {
              case Some(v) => log.info(s"Found old value '$v' by key '$k'")
              case None    => log.warn(s"Key '$k' not found")
            } <* log.info(s"New value '$v' is set by key '$k'")

      def set(k: String, v: String): F[Unit] =
        log.info(s"set(key = '$k', value = '$v')") *>
          redis.set(k, v) *> log.info(s"Value '$v' is set by key '$k'")

      def setArgs(k: String, v: String, as: SetArgs): F[Boolean] =
        log.info(s"setArgs(key = '$k', value = '$v', arguments = $as)") *>
          redis.set(k, v, as).flatTap {
            case true  => log.info(s"Value '$v' is set by key '$k'")
            case false => log.warn(s"Failed to set value '$v' by key '$k'")
          }

      def setNx(k: String, v: String): F[Boolean] =
        log.info(s"setNx(key = '$k', value = '$v')") *>
          redis
            .setNx(k, v)
            .flatTap {
              case true  => log.info(s"Value not found by key '$k', value '$v' is set")
              case false => log.warn(s"Found value by key '$k', no operation is performed")
            }

      def setEx(k: String, v: String, e: FiniteDuration): F[Unit] =
        log.info(s"setEx(key = '$k', value = '$v', expiration = $e)") *>
          redis.setEx(k, v, e) *> log.info(s"Value '$v' is set by key '$k' with expiration $e")

      for {
        _ <- log.info("String commands test")

        _ <- log.info("********** Test 0 **********")

        k0 = "k0"
        v0 = "v0"

        _ <- get(k0)
        _ <- set(k0, v0)
        _ <- strLen(k0)
        _ <- get(k0)
        _ <- del(k0)
        _ <- get(k0)

        _ <- log.info("********** Test 1 **********")

        k1  = "k1"
        v11 = "This is a string"
        v12 = ". Another string"

        _ <- set(k1, v11)
        _ <- getRange(k1, 0, 3)
        _ <- strLen(k1)
        _ <- append(k1, v12)
        _ <- get(k1)
        _ <- strLen(k1)
        _ <- del(k1)

        _ <- log.info("********** Test 2 **********")

        k2  = "k2"
        v21 = "v21"
        v22 = "v22"

        _ <- exists(k2)
        _ <- get(k2)
        _ <- append(k2, v21)
        _ <- get(k2)
        _ <- getSet(k2, v22)
        _ <- get(k2)
        _ <- del(k2)

        _ <- log.info("********** Test 3 **********")

        k3 = "k3"
        v3 = "v3"

        _ <- getSet(k3, v3)
        _ <- get(k3)
        _ <- del(k3)

        _ <- log.info("********** Test 4 **********")

        k4  = "k4"
        v41 = "v41"
        v42 = "v42"

        _ <- setNx(k4, v41)
        _ <- get(k4)
        _ <- setNx(k4, v42)
        _ <- get(k4)
        _ <- del(k4)

        _ <- log.info("********** Test 5 **********")

        k5 = "k5"
        v5 = "v5"

        _ <- setEx(k5, v5, 500.millis)
        _ <- get(k5)
        _ <- sleep(500.millis)
        _ <- get(k5)
        _ <- del(k5)

        _ <- log.info("********** Test 6 **********")

        k6 = "k6"
        v6 = "v6"

        _ <-
          setArgs(
            k6,
            v6,
            SetArgs(
              ex = Nx,             // Only set key if it does not exist
              ttl = Px(500.millis) // Set Expiration in Millis
            )
          )
        _ <- get(k6)
        _ <- sleep(500.milliseconds)
        _ <- get(k6)
        _ <- del(k6)
        _ <-
          setArgs(
            k6,
            v6,
            SetArgs(
              ex = Xx,              // Only set key if it already exists
              ttl = Ex(1000.millis) // Set Expiration in Seconds
            )
          )

        commands =
          set(k0, v0) :: getSet(k1, v11) :: setNx(k2, v21) :: get(k0) :: get(k1) :: get(k2) ::
            del(k0) :: del(k1) :: del(k2) :: HNil

        _ <- log.info("********** Transaction **********")
        _ <-
          RedisTransaction(redis)
            .filterExec(commands) //(hconsUnit)
            .flatMap { case v11 ~: v21 ~: k0 ~: k1 ~: k2 ~: _ ~: _ ~: _ ~: HNil =>
              log.info(
                s"getSet(k1, v11) = $v11, setNx(k2, v21) = $v21, get(k0) = $k0, get(k1) = $k1, get(k2) = $k2"
              )
            }
            .onError {
              case TransactionAborted =>
                log.error("[Error] - Transaction Aborted")
              case TransactionDiscarded =>
                log.error("[Error] - Transaction Discarded")
              case _: TimeoutException =>
                log.error("[Error] - Timeout")
              case e =>
                log.error(s"[Error] - $e")
            }

        _ <- log.info("********** Pipeline **********")
        _ <-
          RedisPipeline(redis)
            .exec(commands)
            .flatMap { case _ ~: v11 ~: v21 ~: k0 ~: k1 ~: k2 ~: _ ~: _ ~: _ ~: HNil =>
              log.info(
                s"getSet(k1, v11) = $v11, setNx(k2, v21) = $v21, get(k0) = $k0, get(k1) = $k1, get(k2) = $k2"
              )
            }
            .onError {
              case PipelineError =>
                log.error("[Error] - Pipeline failed")
              case _: TimeoutException =>
                log.error("[Error] - Timeout")
              case e =>
                log.error(s"[Error] - $e")
            }
      } yield ()
    }
}
