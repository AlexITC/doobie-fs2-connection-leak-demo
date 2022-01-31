ThisBuild / scalaVersion := "2.13.8"
ThisBuild / organization := "net.wiringbits"

lazy val doobieVersion = "1.0.0-RC1"
lazy val server = (project in file("server"))
  .settings(
    name := "doobie-fs2-poc",
    fork := true,
    Test / fork := true, // allows for graceful shutdown of containers once the tests have finished running
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-feature",
      "-target:jvm-1.8",
      "-encoding",
      "UTF-8",
      "-Wconf:src=src_managed/.*:silent",
      "-Xlint:missing-interpolator",
      "-Xlint:adapted-args",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-Ywarn-unused"
    ),
    libraryDependencies ++= Seq(
      "org.postgresql" % "postgresql" % "42.3.1",
      "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.40.0" % "test",
      "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.40.0" % "test"
    ),
    libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.35",
    libraryDependencies += "ch.qos.logback" % "logback-core" % "1.2.10",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.10",
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "doobie-core" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari" % doobieVersion,
      "org.tpolecat" %% "doobie-specs2" % doobieVersion
    ),
    libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.10",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.10" % "test",
    libraryDependencies += "com.h2database" % "h2" % "2.1.210" % Test
  )

lazy val root = (project in file("."))
  .aggregate(
    server
  )
