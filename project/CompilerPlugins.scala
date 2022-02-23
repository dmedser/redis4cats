import sbt._

object CompilerPlugins {

  object Versions {
    val bm4           = "0.3.1"
    val kindProjector = "0.13.2"
  }

  val bm4           = Seq("com.olegpy" %% "better-monadic-for" % Versions.bm4)
  val kindProjector = Seq("org.typelevel" %% "kind-projector" % Versions.kindProjector cross CrossVersion.full)
}
