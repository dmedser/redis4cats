package app

import cats.effect.Async
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object SetCommands extends App.SingleNode {

  protected def program[F[_] : Async]: (Logger[F] => Env[F]) => F[Unit] =
    Slf4jLogger.create.map(_) >>= { env =>
      import env._

      type K = String
      type V = String

      def sIsMember(k: K, v: V): F[Boolean] =
        log.info(s"sIsMember(key = '$k', value = '$v')") *>
          redis.sIsMember(k, v).flatTap {
            case true  => log.info(s"Value '$v' is presented in set with key '$k'")
            case false => log.warn(s"Value '$v' isn't presented in set with key '$k'")
          }

      def sCard(k: K): F[Long] =
        log.info(s"sCard(key = $k)") *>
          redis.sCard(k).flatTap { n =>
            log.info(s"The cardinality (number of elements) of the set stored at key '$k' is $n")
          }

      def getOp(op: String)(f: Seq[K] => F[Set[V]])(ks: K*): F[Set[V]] = {
        val ksStr = ks.mkString("'", "', '", "'")
        log.info(s"s$op(keys = $ksStr)") *>
          f(ks).flatTap { vs =>
            log.info(s"$op of sets: $ksStr is: " + vs.mkString("'", "', '", "'"))
          }
      }

      def sDiff(ks: K*): F[Set[V]] =
        getOp("Diff")(redis.sDiff)(ks: _*)

      def sInter(ks: K*): F[Set[V]] =
        getOp("Inter")(redis.sInter)(ks: _*)

      def sUnion(ks: K*): F[Set[V]] =
        getOp("Union")(redis.sUnion)(ks: _*)

      def sUnionStore(d: K, ks: K*): F[Unit] = {
        val ksStr = ks.mkString("'", "', '", "'")
        log.info(s"sUnionStore(destination = $d, keys = $ksStr)") *>
          redis.sUnionStore(d, ks: _*) *>
          log.info(s"Union of sets $ksStr is stored at key '$d'")
      }

      def sMembers(k: K): F[Set[V]] =
        log.info(s"sMembers(key = '$k')") *>
          redis.sMembers(k).flatTap { vs =>
            log.info(s"Found ${vs.size} values by key '$k': " + vs.mkString("'", "', '", "'"))
          }

      def sRandMember(k: K): F[Option[V]] =
        log.info(s"sRandMember(key = $k)") *>
          redis.sRandMember(k).flatTap {
            case Some(v) => log.info(s"Found random value '$v' by key '$k'")
            case None    => log.warn(s"Key '$k' not found")
          }

      def sRandMembers(k: K, c: Long): F[List[V]] =
        log.info(s"sRandMember(key = $k, count = $c)") *>
          redis.sRandMember(k, c).flatTap { vs =>
            log.info(s"Found ${vs.length} of $c random values by key '$k': " + vs.mkString("'", "', '", "'"))
          }

      def sAdd(k: K, vs: V*): F[Long] = {
        val vsStr = vs.mkString("'", "', '", "'")
        log.info(s"sAdd(key = '$k', values = $vsStr)") *>
          redis
            .sAdd(k, vs: _*)
            .flatTap(n => log.info(s"$n value(s) added by key '$k'"))
      }

      def sStore(op: String)(f: (K, Seq[K]) => F[Long])(d: K, ks: K*): F[Long] = {
        val ksStr = ks.mkString("'", "', '", "'")
        log.info(s"s${op}Store(destination = $d, keys = $ksStr)") *>
          f(d, ks).flatTap { n =>
            log.info(s"$op of sets $ksStr is stored at key '$d', number of elements in the resulting set: $n")
          }
      }

      def sDiffStore(d: K, ks: K*): F[Long] =
        sStore("Diff")(redis.sDiffStore)(d, ks: _*)

      def sInterStore(d: K, ks: K*): F[Long] =
        sStore("Inter")(redis.sInterStore)(d, ks: _*)

      def sMove(s: K, d: K, v: V): F[Boolean] =
        log.info(s"sMove(source = '$s', destination = '$d', value = '$v')") *>
          redis
            .sMove(s, d, v)
            .flatTap {
              case true  => log.info(s"Value '$v' moved from set '$s' to set '$d'")
              case false => log.warn(s"Value '$v' is not a member of set '$s'")
            }

      def sPop(k: K): F[Option[V]] =
        log.info(s"sPop(key = '$k')") *>
          redis.sPop(k).flatTap {
            case Some(v) => log.info(s"Popped $v from set '$k'")
            case None    => log.warn(s"Set '$k' is empty or not found")
          }

      def sPopN(k: K, n: Long): F[Set[V]] =
        log.info(s"sPopN(key = '$k', count = $n)") *>
          redis.sPop(k, n).flatTap { vs =>
            log.info(s"Popped ${vs.size} of $n elements from set '$k': " + vs.mkString("'", "', '", "'"))
          }

      def sRem(k: K, vs: V*): F[Unit] = {
        val vsStr = vs.mkString("'", "', '", "'")
        log.info(s"sRem(key = '$k', values = $vsStr)") *>
          redis.sRem(k, vs: _*) *>
          log.info(s"Values $vsStr removed from set '$k'")
      }

      for {
        _ <- log.info("Set commands test")

        _ <- log.info("********** Test 0 **********")

        k0              = "k0"
        (v01, v02, v03) = ("v01", "v02", "v03")

        _ <- sAdd(k0, v01, v02, v02)
        _ <- sCard(k0)
        _ <- sMembers(k0)
        _ <- sIsMember(k0, v01)
        _ <- sIsMember(k0, v03)
        _ <- del(k0)

        _ <- log.info("********** Test 1 **********")

        k1                        = "k1"
        (v11, v12, v13, v14, v15) = ("v11", "v12", "v13", "v14", "v15")

        _ <- sRandMember(k1)
        _ <- sRandMembers(k1, 3)
        _ <- sAdd(k1, v11, v12, v13, v14, v15)
        _ <- sCard(k1)
        _ <- sRandMember(k1)
        _ <- sRandMember(k1)
        _ <- sRandMember(k1)
        _ <- sRandMembers(k1, 2)
        _ <- sRandMembers(k1, 3)
        _ <- sRandMembers(k1, 4)
        _ <- del(k1)

        _ <- log.info("********** Test 2 **********")

        (k21, k22, k23)    = ("k21", "k22", "k23")
        (v211, v212, v213) = ("1", "2", "3")
        (v221, v222, v223) = ("2", "3", "4")

        _ <- sAdd(k21, v211, v212, v213)
        _ <- sAdd(k22, v221, v222, v223)
        _ <- sDiff(k21, k22)
        _ <- sDiff(k22, k21)
        _ <- sInter(k21, k22)
        _ <- sUnion(k21, k22)
        _ <- sUnionStore(k23, k21, k22)
        _ <- sMembers(k23)
        _ <- del(k21, k22, k23)

        _ <- log.info("********** Test 3 **********")

        (k31, k32, k33, k34, k35) = ("k31", "k32", "k33", "k34", "k35")
        (v311, v312, v313)        = ("1", "2", "3")
        (v321, v322, v323)        = ("2", "3", "4")

        _ <- sAdd(k31, v311, v312, v313)
        _ <- sAdd(k32, v321, v322, v323)
        _ <- sDiffStore(k33, k31, k32)
        _ <- sMembers(k33)
        _ <- sDiffStore(k34, k32, k31)
        _ <- sMembers(k34)
        _ <- sInterStore(k35, k31, k32)
        _ <- sMembers(k35)
        _ <- del(k31, k32, k33, k34, k35)

        _ <- log.info("********** Test 4 **********")

        (k41, k42)         = ("k41", "k42")
        (v411, v412, v413) = ("a", "b", "c")
        (v421, v422, v423) = ("d", "e", "f")

        _ <- sAdd(k41, v411, v412, v413)
        _ <- sAdd(k42, v421, v422, v423)
        _ <- sMove(k41, k42, v413)
        _ <- sMembers(k41)
        _ <- sMembers(k42)
        _ <- del(k41, k42)

        _ <- log.info("********** Test 5 **********")

        k5                             = "k5"
        (v51, v52, v53, v54, v55, v56) = ("v51", "v52", "v53", "v54", "v55", "v56")

        _  <- sAdd(k5, v51, v52, v53, v54, v55, v56)
        _  <- sPop(k5)
        _  <- sMembers(k5)
        _  <- sPopN(k5, 2)
        s0 <- sMembers(k5)
        _  <- sRem(k5, s0.take(2).toSeq: _*)
        s1 <- sMembers(k5)
        _  <- sRem(k5, s1.toSeq: _*)
        _  <- sMembers(k5)
        _  <- del(k5)

      } yield ()
    }
}
