import CompilerPlugins._

libraryDependencies ++= (bm4 ++ kindProjector).map(compilerPlugin)

scalacOptions += "-Ymacro-annotations"