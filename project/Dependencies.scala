import sbt._

object Dependencies {
  object Versions {
    val cats       = "2.7.0"
    val catsEffect = "3.3.5"
    val circe      = "0.14.1"
    val logback    = "1.2.10"
    val log4cats   = "2.2.0"
    val redis4cats = "1.1.1"
  }

  val cats = Seq("cats-core").map("org.typelevel" %% _ % Versions.cats)

  val catsEffect = Seq("cats-effect").map("org.typelevel" %% _ % Versions.catsEffect)

  val circe = Seq("circe-core", "circe-generic", "circe-parser").map("io.circe" %% _ % Versions.circe)

  val logback = Seq("logback-classic").map("ch.qos.logback" % _ % Versions.logback)

  val log4catsSlf4j = Seq("log4cats-slf4j").map("org.typelevel" %% _ % Versions.log4cats)

  val redis4cats =
    Seq(
      "redis4cats-effects",
      "redis4cats-log4cats"
    ).map("dev.profunktor" %% _ % Versions.redis4cats)
}
