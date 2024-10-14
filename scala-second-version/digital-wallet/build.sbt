import sbt.Keys.libraryDependencies

val scala3Version = "3.5.1"

lazy val root = project
  .in(file("."))
  .settings(
    name := "digital-wallet",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    scalacOptions += "-Ypartial-unification",

    libraryDependencies ++= Seq(
    "org.scalameta" %% "munit" % "1.0.0" % Test,
    "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    "org.typelevel" %% "cats-core" % "2.12.0"
  )
)
