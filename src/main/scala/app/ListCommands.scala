package app

import cats.effect.Async
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object ListCommands extends App.SingleNode {

  protected def program[F[_] : Async]: (Logger[F] => Env[F]) => F[Unit] =
    Slf4jLogger.create.map(_) >>= { env =>
      import env._

      def lIndex(k: String, i: Long): F[Option[String]] =
        log.info(s"lIndex(key = '$k', index = $i)") *>
          redis
            .lIndex(k, i)
            .flatTap {
              case Some(v) => log.info(s"Found value '$v' by key '$k' and index $i")
              case None    => log.warn(s"Key '$k' or (and) index $i not found")
            }

      def lLen(k: String): F[Option[Long]] =
        log.info(s"lLen(key = '$k')") *>
          redis
            .lLen(k)
            .flatTap {
              case Some(n) => log.info(s"Number of values by key '$k': $n")
              case None    => log.warn(s"Key '$k' not found")
            }

      def lRange(k: String, s: Long, e: Long): F[List[String]] =
        log.info(s"lRange(key = '$k', start = $s, end = $e)") *>
          redis
            .lRange(k, s, e)
            .flatTap { vs =>
              log.info {
                s"Found ${vs.size} value(s) by key '$k' within [$s, $e] indexes: " +
                  vs.mkString("'", "', '", "'")
              }
            }

      def lInsert(op: String)(f: (String, String, String) => F[Long])(k: String, p: String, v: String): F[Long] =
        log.info(s"lInsert$op(key = '$k', pivot = '$p', v = '$v')") *>
          f(k, p, v)
            .flatTap {
              case -1 => log.warn(s"Pivot '$p' not found by key '$k'")
              case n  => log.info(s"Current number of elements by key '$k': $n")
            }

      def lInsertAfter(k: String, p: String, v: String): F[Long] =
        lInsert("After")(redis.lInsertAfter)(k, p, v)

      def lInsertBefore(k: String, p: String, v: String): F[Long] =
        lInsert("Before")(redis.lInsertBefore)(k, p, v)

      def lRem(k: String, n: Long, v: String): F[Long] =
        log.info(s"lRem(key = '$k', count = $n, value = '$v')") *>
          redis
            .lRem(k, n, v)
            .flatTap(n => log.info(s"Removed $n values '$v' by key '$k'"))

      def lSet(k: String, i: Long, v: String): F[Unit] =
        log.info(s"lSet(key = '$k', index = $i, value = '$v')") *>
          redis.lSet(k, i, v) *>
          log.info(s"Value '$v' is set by key '$k' with index $i")

      def lTrim(k: String, s: Long, e: Long): F[Unit] =
        log.info(s"lTrim(key = '$k', start = $s, stop = $e)") *>
          redis.lTrim(k, s, e) *>
          log.info(s"Trimmed values of key '$k' by indexes [$s, $e]")

      def pop(op: String)(f: String => F[Option[String]])(k: String): F[Option[String]] =
        log.info(s"$op(key = $k)") *>
          f(k).flatTap {
            case Some(v) => log.info(s"Popped value '$v' by key '$k'")
            case None    => log.warn(s"Key '$k' not found")
          }

      def rPop(k: String): F[Option[String]] =
        pop("rPop")(redis.rPop)(k)

      def lPop(k: String): F[Option[String]] =
        pop("lPop")(redis.lPop)(k)

      def push(op: String)(f: (String, Seq[String]) => F[Long])(k: String, vs: String*): F[Long] = {
        val vsStr = vs.mkString("'", "', '", "'")
        log.info(s"$op(key = '$k', values = $vsStr)") *>
          f(k, vs)
            .flatTap(n => log.info(s"Current number of elements by key '$k': $n"))
      }

      def lPush(k: String, vs: String*): F[Long] =
        push("lPush")(redis.lPush)(k, vs: _*)

      def lPushX(k: String, vs: String*): F[Long] =
        push("lPushX")(redis.lPushX)(k, vs: _*)

      def rPush(k: String, vs: String*): F[Long] =
        push("rPush")(redis.rPush)(k, vs: _*)

      def rPushX(k: String, vs: String*): F[Long] =
        push("rPushX")(redis.rPushX)(k, vs: _*)

      def rPopLPush(s: String, d: String): F[Option[String]] =
        log.info(s"rPopLPush(source = '$s', destination = '$d')") *>
          redis
            .rPopLPush(s, d)
            .flatTap {
              case Some(v) => log.info(s"Last value '$v' is removed from '$s' and prepended to '$d'")
              case None    => log.warn(s"Source key '$s' or (and) destination key '$d' not found")
            }

      /*def bPop(op: String)(
          f: (Duration, Nel[String]) => F[Option[(String, String)]]
      )(t: Duration, ks: Nel[String]): F[Option[(String, String)]] = {
        val ksStr = ks.toList.mkString("'", "', '", "'")
        log.info(s"$op(timeout = $t, keys = $ksStr)") *>
          f(t, ks)
            .flatTap {
              case Some((k, v)) => log.info(s"Popped value '$v' by key '$k'")
              case None         => log.warn(s"No element popped during the timeout $t from any of keys: $ksStr")
            }
      }

      def blPop(t: Duration, ks: Nel[String]): F[Option[(String, String)]] =
        bPop("blPop")(redis.blPop)(t, ks)

      def brPop(t: Duration, ks: Nel[String]): F[Option[(String, String)]] =
        bPop("brPop")(redis.brPop)(t, ks)*/

      for {
        _ <- log.info("List commands test")

        _ <- log.info("********** Test 0 **********")

        k0  = "k0"
        v01 = "v01"
        v02 = "v02"
        v03 = "v03"

        _ <- lPush(k0, v01)
        _ <- lIndex(k0, 0)
        _ <- lPush(k0, v02, v03)
        _ <- lIndex(k0, 0)
        _ <- lIndex(k0, 1)
        _ <- lIndex(k0, 2)
        _ <- lLen(k0)
        _ <- del(k0)
        _ <- lLen(k0)

        _ <- log.info("********** Test 1 **********")

        k1  = "k1"
        v11 = "v11"
        v12 = "v12"

        _ <- lIndex(k1, 0)
        _ <- lPush(k1, v11)
        _ <- lIndex(k1, 0)
        _ <- lSet(k1, 0, v12)
        _ <- lIndex(k1, 0)
        _ <- del(k1)

        _ <- log.info("********** Test 2 **********")

        k2  = "k2"
        v21 = "v21"
        v22 = "v22"
        v23 = "v23"

        _ <- rPush(k2, v21, v22, v23)
        _ <- lRange(k2, 0, 2)
        _ <- lRange(k2, 1, 3)
        _ <- del(k2)
        _ <- lPush(k2, v21, v22, v23)
        _ <- lRange(k2, 0, 2)
        _ <- lRange(k2, 0, -1)
        _ <- lRange(k2, 0, -2)
        _ <- del(k2)

        _ <- log.info("********** Test 3 **********")

        k3  = "k3"
        v31 = "v31"
        v32 = "v32"
        v33 = "v33"

        _ <- lPushX(k3, v31, v32, v33)
        _ <- lPush(k3, v31)
        _ <- lPushX(k3, v32, v33)
        _ <- lRange(k3, 0, -1)
        _ <- del(k3)
        _ <- rPushX(k3, v31, v32, v33)
        _ <- rPush(k3, v31)
        _ <- rPushX(k3, v32, v33)
        _ <- lRange(k3, 0, -1)
        _ <- del(k3)

        _ <- log.info("********** Test 4 **********")

        k4  = "k4"
        v41 = "v41"
        v42 = "v42"
        v43 = "v43"

        _ <- lPush(k4, v41, v42, v43)
        _ <- lRange(k4, 0, -1)
        _ <- lPop(k4)
        _ <- rPop(k4)
        _ <- lRange(k4, 0, -1)
        _ <- del(k4)

        _ <- log.info("********** Test 5 **********")

        k51  = "k51"
        v511 = "a"
        v512 = "b"
        v513 = "c"
        k52  = "k52"
        v521 = "x"
        v522 = "y"
        v523 = "z"

        _ <- rPopLPush(k51, k52)
        _ <- rPush(k51, v511, v512, v513)
        _ <- lRange(k51, 0, -1)
        _ <- rPush(k52, v521, v522, v523)
        _ <- lRange(k52, 0, -1)
        _ <- rPopLPush(k51, k52)
        _ <- lRange(k51, 0, -1)
        _ <- lRange(k52, 0, -1)
        _ <- del(k51, k52)
        _ <- rPush(k51, v511, v512, v513)
        _ <- rPopLPush(k51, k51)
        _ <- lRange(k51, 0, -1)
        _ <- del(k51)

        _ <- log.info("********** Test 6 **********")

        k6  = "k6"
        v61 = "a"
        v62 = "b"
        v63 = "c"
        v64 = "x"
        v65 = "y"
        v66 = "z"

        _ <- rPush(k6, v61, v62, v63, v64, v65, v66)
        _ <- lRange(k6, 0, -1)
        _ <- lTrim(k6, 2, 3)
        _ <- lRange(k6, 0, -1)
        _ <- del(k6)

        _ <- log.info("********** Test 7 **********")

        k7 = "k7"

        (v71, v72, v73, v74, v75, v76, v77) = ("a", "b", "c", "x", "x", "x", "y")

        _ <- rPush(k7, v71, v72, v73, v74, v75, v76, v77)
        _ <- lRange(k7, 0, -1)
        _ <- lRem(k7, 2, "x")
        _ <- lRange(k7, 0, -1)
        _ <- del(k7)

        _ <- log.info("********** Test 8 **********")

        k8  = "k8"
        v81 = "v81"
        v82 = "v82"
        v83 = "v83"

        _ <- lInsertAfter(k8, v81, v82)
        _ <- lInsertBefore(k8, v81, v82)
        _ <- rPush(k8, v81)
        _ <- lInsertAfter(k8, v81, v82)
        _ <- lRange(k8, 0, -1)
        _ <- lInsertBefore(k8, v81, v82)
        _ <- lRange(k8, 0, -1)
        _ <- lInsertAfter(k8, v82, v83)
        _ <- lRange(k8, 0, -1)
        _ <- lInsertBefore(k8, v82, v83)
        _ <- lRange(k8, 0, -1)
        _ <- del(k8)

      } yield ()
    }
}
