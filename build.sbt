


lazy val routingMonad = (project in file(".")).settings(
  name := "routingMonad",
  version := "1.0",
  scalaVersion := "2.11.7",
  libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % "2.2.5"
  )
)