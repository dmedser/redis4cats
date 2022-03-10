package app

import cats.effect.Async
import cats.syntax.apply._
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object HashCommands extends App.Cluster {

  protected def program[F[_] : Async]: (Logger[F] => Env[F]) => F[Unit] = {
    Slf4jLogger.create.map(_) >>= { env =>
      import env._

      type K = String
      type V = String

      def hSet(k: K, f: K, v: V): F[Boolean] =
        log.info(s"hSet(key = '$k', field = '$f', value = '$v')") *>
          redis.hSet(k, f, v).flatTap {
            /* true if field is a new field in the hash and value was set.
               false if field already exists in the hash and the value was updated. */
            case true  => log.info(s"New field '$f' with value '$v' added to hash '$k'")
            case false => log.info(s"Field '$f' updated with value '$v' in hash '$k'")
          }

      def hSetN(k: K, fvs: Map[K, V]): F[Long] = {
        val fvsStr = fvs.map { case (f, v) => s"'$f':'$v'" }.mkString("(", "), (", ")")
        log.info(s"hSetN(key = '$k', fieldValues = $fvsStr)") *>
          redis.hSet(k, fvs).flatTap { n =>
            log.info(s"$n field-values pairs: $fvsStr added to hash '$k'")
          }
      }

      def hSetNx(k: K, f: K, v: V): F[Boolean] =
        log.info(s"hSetNx(key = '$k', field = '$f', value = '$v')") *>
          redis.hSetNx(k, f, v).flatTap {
            case true  => log.info(s"Field-value pair '$f':'$v' added to hash '$k'")
            case false => log.warn(s"Field '$f' already exists in hash '$k'")
          }

      def hExists(k: K, f: K): F[Boolean] =
        log.info(s"hExists(key = '$k', field = '$f')") *>
          redis.hExists(k, f).flatTap {
            case true  => log.info(s"Field '$f' exists in hash '$k'")
            case false => log.warn(s"Field '$f' doesn't exist in hash '$k'")
          }

      def hGet(k: K, f: K): F[Option[V]] =
        log.info(s"hGet(key = '$k', field = '$f')") *>
          redis.hGet(k, f).flatTap {
            case Some(v) => log.info(s"Value '$v' found in hash '$k' by field '$f'")
            case None    => log.warn(s"Field '$f' not found in hash '$k'")
          }

      def hGetAll(k: K): F[Map[K, V]] =
        log.info(s"hGetAll(key = '$k')") *>
          redis.hGetAll(k).flatTap { fvs =>
            log.info {
              s"Found ${fvs.size} field-value pairs in hash '$k': " +
                fvs.map { case (f, v) => s"'$f':'$v'" }.mkString("(", "), (", ")")
            }
          }

      def hmGet(k: K, fs: K*): F[Map[K, V]] = {
        val fsStr = fs.mkString("'", "', '", "'")
        log.info(s"hmGet(key = '$k', fields = $fsStr)") *>
          redis.hmGet(k, fs: _*).flatTap { fvs =>
            log.info {
              s"Found ${fvs.size} field-value pairs in hash '$k': " +
                fvs.map { case (f, v) => s"'$f':'$v'" }.mkString("(", "), (", ")")
            }
          }
      }

      def hKeys(k: K): F[List[K]] =
        log.info(s"hKeys(key = '$k')") *>
          redis.hKeys(k).flatTap { fs =>
            log.info(s"Found ${fs.size} fields in hash '$k': " + fs.mkString("'", "', '", "'"))
          }

      def hVals(k: K): F[List[V]] =
        log.info(s"hVals(key = '$k')") *>
          redis.hVals(k).flatTap { vs =>
            log.info(s"Found ${vs.size} values in hash '$k': " + vs.mkString("'", "', '", "'"))
          }

      def hStrLen(k: K, f: K): F[Option[Long]] =
        log.info(s"hStrLen(key = '$k', field = '$f')") *>
          redis.hStrLen(k, f).flatTap {
            case Some(l) => log.info(s"Length of value of field '$f' in hash '$k' is $l")
            case None    => log.warn(s"Hash '$k' not found")
          }

      def hLen(k: K): F[Option[Long]] =
        log.info(s"hLen(key = '$k')") *>
          redis.hLen(k).flatTap {
            case Some(n) => log.info(s"Found $n fields in hash '$k'")
            case None    => log.warn(s"Hash '$k' not found")
          }

      def hIncrBy(k: K, f: K, a: Long): F[Long] =
        log.info(s"hIncrBy(key = '$k', field = '$f', amount = $a)") *>
          redis
            .hIncrBy(k, f, a)
            .flatTap { r =>
              log.info(s"Result value $r")
            }
            .handleErrorWith {
              log
                .error(_)(s"Failed to increment value of field '$f' in hash '$k' by amount $a. Default value 0 returned")
                .as(0L)
            }

      def hIncrByFloat(k: K, f: K, a: Double): F[Double] =
        log.info(s"hIncrByFloat(key = '$k', field = '$f', amount = $a)") *>
          redis
            .hIncrByFloat(k, f, a)
            .flatTap { r =>
              log.info(s"Result value $r")
            }
            .handleErrorWith {
              log
                .error(_)(s"Failed to increment value of field '$f' in hash '$k' by amount $a. Default value 0.0 returned")
                .as(0.0)
            }

      def hDel(k: K, fs: K*): F[Long] = {
        val fsStr = fs.mkString("'", "', '", "'")
        log.info(s"hDel(key = '$k', fields = $fsStr)") *>
          redis.hDel(k, fs: _*).flatTap { n =>
            log.info(s"Deleted $n field-value pairs from key '$k'")
          }
      }

      for {
        _ <- log.info("Hash commands test")

        _ <- log.info("********** Test 0 **********")

        k0         = "k0"
        (f01, v01) = "f01" -> "v01"
        (f02, v02) = "f02" -> "v02"
        (f03, v03) = "f03" -> "v03"

        _ <- hSet(k0, f01, v01)
        _ <- hExists(k0, f01)
        _ <- hExists(k0, f02)
        _ <- hGet(k0, f01)
        _ <- hGet(k0, f02)
        _ <- hSetN(k0, Map(f02 -> v02, f03 -> v03))
        _ <- hGetAll(k0)
        _ <- hDel(k0, f01)
        _ <- hGetAll(k0)
        _ <- del(k0)

        _ <- log.info("********** Test 1 **********")

        k1         = "k1"
        (f11, v11) = "f11" -> "v11"
        (f12, v12) = "f12" -> "v12"
        (f13, v13) = "f13" -> "v13"

        _ <- hSetN(k1, Map(f11 -> v11, f12 -> v12, f13 -> v13))
        _ <- hmGet(k1, f11, f12)
        _ <- hKeys(k1)
        _ <- hVals(k1)
        _ <- hStrLen(k1, f11)
        _ <- hLen(k1)
        _ <- del(k1)

        _ <- log.info("********** Test 2 **********")

        k2       = "k2"
        (f2, v2) = "f2" -> "v2"

        _ <- hSetNx(k2, f2, v2)
        _ <- hSetNx(k2, f2, v2)
        _ <- del(k2)

        _ <- log.info("********** Test 3 **********")

        k3         = "k2"
        (f31, v31) = "f31" -> "1"
        (f32, v32) = "f32" -> "10.0"
        (f33, v33) = "f33" -> "a"

        _ <- hSetN(k3, Map(f31 -> v31, f32 -> v32, f33 -> v33))
        _ <- hIncrBy(k3, f31, 10L)
        _ <- hIncrByFloat(k3, f32, 2.34)
        _ <- hIncrBy(k3, f33, 1L)
        _ <- hIncrByFloat(k3, f33, -12.34)
        _ <- del(k3)

      } yield ()
    }
  }
}
