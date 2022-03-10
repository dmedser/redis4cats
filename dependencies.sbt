import Dependencies._

libraryDependencies ++= {

  val compile =
    cats ++
      catsEffect ++
      circe ++
      logback ++
      log4cats ++
      redis4cats

  val test = Seq.empty[ModuleID]

  compile ++ test.map(_ % Test)
}
