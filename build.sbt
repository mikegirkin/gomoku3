val scala3Version = "3.1.1"
val http4sVersion = "0.23.7"
val doobieVersion = "1.0.0-RC2"

lazy val root = project
  .in(file("."))
  .settings(
    name := "gomoku3",
    version := "0.1.0",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl"          % http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % http4sVersion,

      "org.tpolecat" %% "doobie-core"     % doobieVersion,
      "org.tpolecat" %% "doobie-hikari"   % doobieVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,

      "commons-codec" % "commons-codec" % "1.15",

      "org.scalatest" %% "scalatest" % "3.2.9" % Test
    )
  )
