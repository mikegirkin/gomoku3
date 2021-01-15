val scala3Version = "3.0.0-M3"

lazy val root = project
  .in(file("."))
  .settings(
    name := "gomoku3",
    version := "0.1.0",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % "2.5.0",

      "org.scalatest" %% "scalatest" % "3.2.3" % "test"
    )
  )
