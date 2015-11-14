


lazy val routingMonad = (project in file(".")).settings(
  name := "routingMonad",
  version := "1.0",
  scalaVersion := "2.11.7",
  libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % "2.2.5",
    "org.typelevel" % "scala-reflect" % "2.11.7",
    "org.monifu" %% "monifu" % "1.0-RC3",
    "org.eclipse.californium" % "californium-core" % "1.0.0-RC2",
    "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"
  ),
  dependencyOverrides += "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.4"
).enablePlugins(JmhPlugin)

