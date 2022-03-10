Seq(
  "io.github.davidgregory084" % "sbt-tpolecat" % "0.1.22",
  "com.timushev.sbt"          % "sbt-updates"  % "0.6.1",
  "com.scalapenos"            % "sbt-prompt"   % "1.0.2",
  "se.marcuslonnberg"         % "sbt-docker"   % "1.8.3",
  "com.eed3si9n"              % "sbt-assembly" % "1.2.0"
).map(addSbtPlugin)
