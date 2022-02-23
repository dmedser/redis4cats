package app

import cats.effect.Async
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import dev.profunktor.redis4cats.effects._
import io.lettuce.core.GeoArgs
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object GeoCommands extends App {

  protected def program[F[_] : Async]: (Logger[F] => Env[F]) => F[Unit] =
    Slf4jLogger.create.map(_) >>= { env =>
      import env._

      type K = String
      type V = String

      def geoAdd(k: K, gvs: GeoLocation[V]*): F[Unit] = {
        val gvsStr =
          gvs
            .map(gl => s"lat = ${gl.lat.value}, lon = ${gl.lon.value}, val = '${gl.value}'")
            .mkString("(", "), (", ")")
        log.info(s"geoAdd(key = '$k', geoValues = $gvsStr)") *>
          redis.geoAdd(k, gvs: _*) *>
          log.info(s"${gvs.size} geo values stored at key '$k'")
      }

      def geoDist(k: K, f: V, t: V, u: GeoArgs.Unit): F[Double] =
        log.info(s"geoDist(key = '$k', from = '$f', to = '$t', units = $u)") *>
          redis.geoDist(k, f, t, u).flatTap { d =>
            log.info(s"Distance between '$f' and '$t' is $d $u")
          }

      def geoPos(k: K, vs: V*): F[List[GeoCoordinate]] = {
        val vsStr = vs.mkString("'", "', '", "'")
        log.info(s"geoPos(key = '$k', values = $vsStr)") *>
          redis.geoPos(k, vs: _*).flatTap { gcs =>
            val cs = vs.toList.zip(gcs).map { case (v, gc) => s"'$v': (lat = ${gc.y}, lon = ${gc.x})" }.mkString("\n", ",\n", "\n")
            log.info(s"Coordinates by key '$k': " + cs)
          }
      }

      def geoRadius(k: K, gr: GeoRadius, u: GeoArgs.Unit): F[Set[V]] =
        log.info(s"geoRadius(key = '$k', geoRadius = (lat = ${gr.lat.value}, lon = ${gr.lon.value}, dist = ${gr.dist.value}), unit = $u)") *>
          redis.geoRadius(k, gr, u).flatTap { vs =>
            log.info(
              s"Points within a radius of ${gr.dist.value} $u from (lat = ${gr.lat.value}, lon = ${gr.lon.value}): " +
                vs.mkString("'", "', '", "'")
            )
          }

      def geoRadiusArgs(k: K, gr: GeoRadius, u: GeoArgs.Unit, as: GeoArgs): F[List[GeoRadiusResult[V]]] =
        log.info(
          s"geoRadiusArgs(key = '$k', geoRadius = (lat = ${gr.lat.value}, lon = ${gr.lon.value}, dist = ${gr.dist.value}), unit = $u, args = (withdistance = ${as.isWithDistance}, withcoordinates = ${as.isWithCoordinates}, withhash = ${as.isWithHash}))"
        ) *>
          redis.geoRadius(k, gr, u, as).flatTap { vs =>
            log.info {
              s"Points within a radius of ${gr.dist.value} $u from (lat = ${gr.lat.value}, lon = ${gr.lon.value}): " +
                vs
                  .map { r =>
                    s"val = ${r.value}, dist = ${r.dist.value} $u, hash = ${r.hash.value}, coordinate = (lat = ${r.coordinate.y}, lon = ${r.coordinate.x})"
                  }
                  .mkString("\n", ",\n", "\n")
            }
          }

      def geoRadiusByMember(k: K, v: V, d: Distance, u: GeoArgs.Unit): F[Set[V]] =
        log.info(s"geoRadiusByMember(key = '$k', value = '$v', distance = ${d.value} unit = $u)") *>
          redis.geoRadiusByMember(k, v, d, u).flatTap { vs =>
            log.info {
              s"Points within a radius of ${d.value} $u from '$v': " +
                vs.mkString("'", "', '", "'")
            }
          }

      def geoRadiusByMemberArgs(k: K, v: V, d: Distance, u: GeoArgs.Unit, as: GeoArgs): F[List[GeoRadiusResult[V]]] =
        log.info(
          s"geoRadiusByMemberArgs(key = '$k', value = '$v', distance = ${d.value} unit = $u, args = (withdistance = ${as.isWithDistance}, withcoordinates = ${as.isWithCoordinates}, withhash = ${as.isWithHash}))"
        ) *>
          redis.geoRadiusByMember(k, v, d, u, as).flatTap { vs =>
            log.info {
              s"Points within a radius of ${d.value} $u from '$v': " +
                vs
                  .map { r =>
                    s"val = ${r.value}, dist = ${r.dist.value} $u, hash = ${r.hash.value}, coordinate = (lat = ${r.coordinate.y}, lon = ${r.coordinate.x})"
                  }
                  .mkString("\n", ",\n", "\n")
            }
          }

      for {
        _ <- log.info("Geo commands test")

        _ <- log.info("********** Test 0.1 **********")

        k0  = "k0"
        v01 = GeoLocation(lat = Latitude(56.859625), lon = Longitude(35.911860), value = "Tver")
        v02 = GeoLocation(lat = Latitude(56.257900), lon = Longitude(90.492107), value = "Achinsk")
        v03 = GeoLocation(lat = Latitude(56.838011), lon = Longitude(60.597474), value = "Ekaterinburg")

        _ <- geoDist(k0, v01.value, v02.value, GeoArgs.Unit.km)
        _ <- geoRadius(k0, GeoRadius(lat = v03.lat, lon = v03.lon, dist = Distance(3000.0)), GeoArgs.Unit.km)
        _ <- geoRadiusArgs(k0, GeoRadius(lat = v03.lat, lon = v03.lon, dist = Distance(3000.0)), GeoArgs.Unit.km, GeoArgs.Builder.full())
        _ <- geoRadiusByMember(k0, v03.value, Distance(3000.0), GeoArgs.Unit.km)
        _ <- geoRadiusByMemberArgs(k0, v03.value, Distance(3000.0), GeoArgs.Unit.km, GeoArgs.Builder.full())

        _ <- log.info("********** Test 0.2 **********")

        _ <- geoAdd(k0, v01, v02)
        _ <- geoDist(k0, v01.value, v02.value, GeoArgs.Unit.km)
        _ <- geoPos(k0, v01.value, v02.value)
        _ <- geoRadius(k0, GeoRadius(lat = v03.lat, lon = v03.lon, dist = Distance(3000.0)), GeoArgs.Unit.km)
        _ <- geoRadiusArgs(k0, GeoRadius(lat = v03.lat, lon = v03.lon, dist = Distance(3000.0)), GeoArgs.Unit.km, GeoArgs.Builder.full())
        _ <- geoAdd(k0, v03)
        _ <- geoRadiusByMember(k0, v03.value, Distance(3000.0), GeoArgs.Unit.km)
        _ <- geoRadiusByMemberArgs(k0, v03.value, Distance(3000.0), GeoArgs.Unit.km, GeoArgs.Builder.full())
        _ <- del(k0)

      } yield ()
    }
}
