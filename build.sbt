import com.scalapenos.sbt.prompt.PromptTheme

name := "redis4cats"

version := "0.1"

scalaVersion := "2.13.8"

addCommandAlias("deps", "dependencyUpdates")

promptTheme :=
  PromptTheme(
    List(
      gitBranch(clean = fg(green), dirty = fg(yellow)),
      currentProject(fg(105)).padLeft(" [").padRight("] λ ")
    )
  )
