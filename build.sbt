import com.scalapenos.sbt.prompt.PromptTheme

name := "redis4cats"

version := "0.1"

scalaVersion := "2.13.8"

enablePlugins(DockerPlugin)

inTask(assembly) {
  Seq(
    assemblyJarName := s"${name.value}-${version.value}.jar",
    assemblyMergeStrategy := {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.discard
      case x =>
        val currentStrategy = (assembly / assemblyMergeStrategy).value
        currentStrategy(x)
    }
  )
}

inTask(docker) {
  Seq(
    imageNames := Seq(ImageName(s"${name.value}:latest")),
    dockerfile :=
      new Dockerfile {

        val runtimePath = "/"

        val entryPointSh = "/start-cluster-test.sh"

        from("openjdk:11-jre-slim-buster")

        Seq(
          assembly.value                                                             -> runtimePath, // sources
          (Compile / sourceDirectory).value / "operations" / "start-cluster-test.sh" -> runtimePath  // entry point
        ) foreach { case (source, destination) => add(source, destination) }

        runShell("chmod", "+x", entryPointSh)
        workDir(runtimePath)
        entryPoint(entryPointSh)
      },
    buildOptions := BuildOptions(removeIntermediateContainers = BuildOptions.Remove.OnSuccess)
  )
}

addCommandAlias("deps", "dependencyUpdates")

promptTheme :=
  PromptTheme(
    List(
      gitBranch(clean = fg(green), dirty = fg(yellow)),
      currentProject(fg(105)).padLeft(" [").padRight("] λ ")
    )
  )
