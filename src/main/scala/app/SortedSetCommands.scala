package app

import cats.data.{NonEmptyList => Nel}
import cats.effect.Async
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.list._
import dev.profunktor.redis4cats.effects.{RangeLimit, Score, ScoreWithValue, ZRange}
import io.lettuce.core.ZAddArgs
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object SortedSetCommands extends App {

  private sealed trait ZAddArgs0 {
    override def toString: String =
      this match {
        case ZAddArgs0.NX => "nx"
        case ZAddArgs0.XX => "xx"
        case ZAddArgs0.CH => "ch"
        case ZAddArgs0.LT => "lt"
        case ZAddArgs0.GT => "gt"
      }
  }

  private object ZAddArgs0 {
    case object NX extends ZAddArgs0
    case object XX extends ZAddArgs0
    case object CH extends ZAddArgs0
    case object LT extends ZAddArgs0
    case object GT extends ZAddArgs0
  }

  protected def program[F[_] : Async]: (Logger[F] => Env[F]) => F[Unit] =
    Slf4jLogger.create.map(_) >>= { env =>
      import env._

      type K = String
      type V = String

      def zAdd(k: K)(svs: ScoreWithValue[V]*)(as: ZAddArgs0*): F[Long] = {
        def toZAddArgs(args: Nel[ZAddArgs0]): ZAddArgs = {
          val fst =
            args.head match {
              case ZAddArgs0.NX => ZAddArgs.Builder.nx()
              case ZAddArgs0.XX => ZAddArgs.Builder.xx()
              case ZAddArgs0.CH => ZAddArgs.Builder.ch()
              case ZAddArgs0.LT => ZAddArgs.Builder.lt()
              case ZAddArgs0.GT => ZAddArgs.Builder.gt()
            }
          args.tail.foreach {
            case ZAddArgs0.NX => fst.nx()
            case ZAddArgs0.XX => fst.xx()
            case ZAddArgs0.CH => fst.ch()
            case ZAddArgs0.LT => fst.lt()
            case ZAddArgs0.GT => fst.gt()
          }
          fst
        }

        val svsStr = svs.map(sv => s"'${sv.value}':${sv.score.value}").mkString("(", "), (", ")")

        val asStr = as.mkString("(", ", ", ")")

        log.info(s"zAdd(key = '$k', args = $asStr, values = $svsStr") *>
          redis.zAdd(k, as.toList.toNel.map(toZAddArgs), svs: _*).flatTap { r =>
            log.info(s"Result is $r")
          }
      }

      def zCard(k: K): F[Option[Long]] =
        log.info(s"zCard(key = '$k')") *>
          redis.zCard(k).flatTap {
            case Some(n) => log.info(s"The cardinality (number of elements) of the set stored at key '$k' is $n")
            case None    => log.warn(s"Key '$k' not found")
          }

      def zCount(k: K, r: ZRange[Double]): F[Option[Long]] =
        log.info(s"zCount(key = '$k', start = ${r.start}, end = ${r.end})") *>
          redis.zCount(k, r).flatTap {
            case Some(c) => log.info(s"Count of values which scores are within [${r.start}, ${r.end}] is $c")
            case None    => log.warn(s"Key '$k' not found")
          }

      def zRange(op: String)(f: (K, Long, Long) => F[List[V]])(k: K, s: Long, e: Long): F[List[V]] =
        log.info(s"$op(key = '$k', start = $s, stop = $e)") *>
          f(k, s, e).flatTap { vs =>
            log.info {
              s"Found ${vs.size} value(s) by key '$k' within [$s, $e] indexes: " +
                vs.mkString("'", "', '", "'")
            }
          }

      def zDirRange(k: K, s: Long, e: Long): F[List[V]] =
        zRange("zRange")(redis.zRange)(k, s, e)

      def zRevRange(k: K, s: Long, e: Long): F[List[V]] =
        zRange("zRevRange")(redis.zRevRange)(k, s, e)

      def zRangeWithScores(op: String)(f: (K, Long, Long) => F[List[ScoreWithValue[V]]])(k: K, s: Long, e: Long): F[List[ScoreWithValue[V]]] =
        log.info(s"$op(key = '$k', start = $s, stop = $e)") *>
          f(k, s, e).flatTap { svs =>
            log.info {
              s"Found ${svs.size} value(s) by key '$k' within [$s, $e] indexes: " +
                svs.map(sv => s"'${sv.value}':${sv.score.value}").mkString("(", "), (", ")")
            }
          }

      def zDirRangeWithScores(k: K, s: Long, e: Long): F[List[ScoreWithValue[V]]] =
        zRangeWithScores("zRangeWithScores")(redis.zRangeWithScores)(k, s, e)

      def zRevRangeWithScores(k: K, s: Long, e: Long): F[List[ScoreWithValue[V]]] =
        zRangeWithScores("zRevRangeWithScores")(redis.zRevRangeWithScores)(k, s, e)

      def zRangeByScore(op: String)(
          f: (K, ZRange[Double], Option[RangeLimit]) => F[List[V]]
      )(k: K, r: ZRange[Double], l: Option[RangeLimit]): F[List[V]] =
        log.info(s"$op(key = '$k', start = ${r.start}, end = ${r.end}${l.foldMap(l => s", offset = ${l.offset}, count = ${l.count}")})") *>
          f(k, r, l).flatTap { vs =>
            log.info {
              s"Found ${vs.size} value(s) by key '$k' within [${r.start}, ${r.end}] scores: " +
                vs.mkString("'", "', '", "'")
            }
          }

      def zDirRangeByScore(k: K, r: ZRange[Double], l: Option[RangeLimit] = None): F[List[V]] =
        zRangeByScore("zRangeByScore")(redis.zRangeByScore)(k, r, l)

      def zRevRangeByScore(k: K, r: ZRange[Double], l: Option[RangeLimit] = None): F[List[V]] =
        zRangeByScore("zRevRangeByScore")(redis.zRevRangeByScore)(k, r, l)

      def zRangeByScoreWithScores(op: String)(
          f: (K, ZRange[Double], Option[RangeLimit]) => F[List[ScoreWithValue[V]]]
      )(
          k: K,
          r: ZRange[Double],
          l: Option[RangeLimit]
      ): F[List[ScoreWithValue[V]]] =
        log.info(
          s"$op(key = '$k', start = ${r.start}, end = ${r.end}${l.foldMap(l => s", offset = ${l.offset}, count = ${l.count}")})"
        ) *>
          f(k, r, l).flatTap { svs =>
            log.info {
              s"Found ${svs.size} value(s) by key '$k' within [${r.start}, ${r.end}] scores: " +
                svs.map(sv => s"'${sv.value}':${sv.score.value}").mkString("(", "), (", ")")
            }
          }

      def zDirRangeByScoreWithScores(
          k: K,
          r: ZRange[Double],
          l: Option[RangeLimit] = None
      ): F[List[ScoreWithValue[V]]] =
        zRangeByScoreWithScores("zRangeByScoreWithScores")(redis.zRangeByScoreWithScores)(k, r, l)

      def zRevRangeByScoreWithScores(
          k: K,
          r: ZRange[Double],
          l: Option[RangeLimit] = None
      ): F[List[ScoreWithValue[V]]] =
        zRangeByScoreWithScores("zRevRangeByScoreWithScores")(redis.zRevRangeByScoreWithScores)(k, r, l)

      def zRank(op: String)(f: (K, V) => F[Option[Long]])(k: K, v: V): F[Option[Long]] =
        log.info(s"$op(key = '$k', value = '$v')") *>
          f(k, v).flatTap {
            case Some(i) => log.info(s"Rank of value '$v' stored at key '$k' is $i")
            case None    => log.warn(s"Key '$k' or value '$v' not found")
          }

      def zDirRank(k: K, v: V): F[Option[Long]] =
        zRank("zRank")(redis.zRank)(k, v)

      def zRevRank(k: K, v: V): F[Option[Long]] =
        zRank("zRevRank")(redis.zRevRank)(k, v)

      def zScore(k: K, v: V): F[Option[Double]] =
        log.info(s"zScore(key = '$k', value = '$v')") *>
          redis.zScore(k, v).flatTap {
            case Some(s) => log.info(s"Score of the value '$v' at key '$k' is $s")
            case None    => log.warn(s"Key '$k' not found")
          }

      def zPop(op: String)(f: (K, Long) => F[List[ScoreWithValue[V]]])(k: K, c: Long): F[List[ScoreWithValue[V]]] =
        log.info(s"zPop$op(key = '$k', count = $c)") *>
          f(k, c).flatTap { svs =>
            log.info {
              s"Popped ${svs.size} of $c elements from set '$k': " +
                svs.map(sv => s"'${sv.value}':${sv.score.value}").mkString("(", "), (", ")")
            }
          }

      def zPopMin(k: K, c: Long): F[List[ScoreWithValue[V]]] =
        zPop("Min")(redis.zPopMin)(k, c)

      def zPopMax(k: K, c: Long): F[List[ScoreWithValue[V]]] =
        zPop("Max")(redis.zPopMax)(k, c)

      for {
        _ <- log.info("Sorted set commands test")

        _ <- log.info("********** Test 0 **********")

        k0  = "k0"
        v01 = ScoreWithValue(Score(1.1), "a")
        v02 = ScoreWithValue(Score(2.2), "b")
        v03 = ScoreWithValue(Score(3.3), "c")
        v04 = ScoreWithValue(Score(4.4), "d")

        _ <- zAdd(k0)(v01, v02, v03, v04)()
        _ <- zCard(k0)
        _ <- zCount(k0, ZRange(-100.0, 100.0))
        _ <- zCount(k0, ZRange(2.0, 4.0))
        _ <- zScore(k0, v01.value)
        _ <- zScore(k0, v04.value)
        _ <- zPopMax(k0, 1L)
        _ <- zCard(k0)
        _ <- zPopMin(k0, 2)
        _ <- zCard(k0)
        _ <- del(k0)

        _ <- log.info("********** Test 1 **********")

        k11 = "k11"
        k12 = "k12"
        v11 = ScoreWithValue(Score(5.5), "e")
        v12 = ScoreWithValue(Score(6.6), "f")
        v13 = ScoreWithValue(Score(7.7), "g")
        v14 = ScoreWithValue(Score(8.8), "h")
        v15 = ScoreWithValue(Score(9.9), "i")

        _ <- zAdd(k11)(v11, v12, v13, v14)()
        _ <- zDirRange(k11, 0, 3)
        _ <- zDirRange(k11, 1, 2)
        _ <- zRevRange(k11, 0, 3)
        _ <- zRevRange(k11, 1, 2)
        _ <- zDirRangeWithScores(k11, 0, 3)
        _ <- zDirRangeWithScores(k11, 1, 2)
        _ <- zRevRangeWithScores(k11, 0, 3)
        _ <- zRevRangeWithScores(k11, 1, 2)
        _ <- zDirRangeByScore(k11, ZRange(0.0, 10.0))
        _ <- zDirRangeByScore(k11, ZRange(6.0, 8.0))
        _ <- zRevRangeByScore(k11, ZRange(0.0, 10.0))
        _ <- zRevRangeByScore(k11, ZRange(6.0, 8.0))
        _ <- zDirRangeByScoreWithScores(k11, ZRange(0.0, 10.0))
        _ <- zDirRangeByScoreWithScores(k11, ZRange(6.0, 8.0))
        _ <- zRevRangeByScoreWithScores(k11, ZRange(0.0, 10.0))
        _ <- zRevRangeByScoreWithScores(k11, ZRange(6.0, 8.0))
        _ <- zDirRank(k11, v13.value)
        _ <- zRevRank(k11, v13.value)
        _ <- zDirRank(k11, v15.value)
        _ <- zRevRank(k11, v15.value)
        _ <- zDirRank(k12, v15.value)
        _ <- zRevRank(k12, v15.value)
        _ <- del(k11)

        _ <- log.info("********** Test 2 **********")

        k2  = "k2"
        v21 = ScoreWithValue(Score(10.10), "j")
        v22 = ScoreWithValue(Score(11.11), v21.value)
        v23 = ScoreWithValue(Score(12.12), v21.value)
        v24 = ScoreWithValue(Score(13.13), v21.value)

        _ <- zAdd(k2)(v21)(ZAddArgs0.XX)
        _ <- zAdd(k2)(v21)(ZAddArgs0.NX)
        _ <- zDirRangeWithScores(k2, 0, -1)
        _ <- zAdd(k2)(v23)(ZAddArgs0.CH)
        _ <- zDirRangeWithScores(k2, 0, -1)
        _ <- zAdd(k2)(v22)(ZAddArgs0.GT, ZAddArgs0.CH) // 11.11 > 12.12 ? 12.12
        _ <- zAdd(k2)(v22)(ZAddArgs0.LT, ZAddArgs0.CH) // 11.11 < 12.12 ? 11.11
        _ <- zDirRangeWithScores(k2, 0, -1)
        _ <- zAdd(k2)(v24)(ZAddArgs0.LT, ZAddArgs0.CH) // 13.13 < 11.11 ? 11.11
        _ <- zAdd(k2)(v24)(ZAddArgs0.GT, ZAddArgs0.CH) // 13.13 > 11.11 ? 13.13
        _ <- zDirRangeWithScores(k2, 0, -1)
        _ <- del(k2)

        _ <- log.info("********** Test 3 **********")

        k3  = "k3"
        v31 = ScoreWithValue(Score(1.0), "1")
        v32 = ScoreWithValue(Score(2.0), "2")
        v33 = ScoreWithValue(Score(3.0), "3")
        v34 = ScoreWithValue(Score(4.0), "4")
        v35 = ScoreWithValue(Score(5.0), "5")
        v36 = ScoreWithValue(Score(6.0), "6")

        _ <- zAdd(k3)(v31, v32, v33, v34, v35, v36)()
        _ <- zDirRangeByScore(k3, ZRange(0.0, 7.0), Some(RangeLimit(1, 4)))
        _ <- zRevRangeByScore(k3, ZRange(0.0, 7.0), Some(RangeLimit(1, 4)))
        _ <- zDirRangeByScoreWithScores(k3, ZRange(0.0, 7.0), Some(RangeLimit(1, 4)))
        _ <- zRevRangeByScoreWithScores(k3, ZRange(0.0, 7.0), Some(RangeLimit(1, 4)))
        _ <- del(k3)

      } yield ()
    }
}
