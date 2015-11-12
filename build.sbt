


lazy val routingMonad = (project in file(".")).settings(
  name := "routingMonad",
  version := "1.0",
  scalaVersion := "2.11.7",
  libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % "2.2.5",
    "org.typelevel" % "scala-reflect" % "2.11.7",
    "org.monifu" %% "monifu" % "1.0-RC3",
    "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"
  )
).enablePlugins(JmhPlugin)

