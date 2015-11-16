import sbtassembly.AssemblyPlugin.autoImport._
import sbt.Keys._

lazy val Server = config("Server").extend(Compile).describedAs("Blocking server compile")
lazy val Client = config("Client").extend(Compile).describedAs("Client")
val buildJars = taskKey[Unit]("build all jars")

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
  dependencyOverrides += "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.4",
  inConfig(Server)(AssemblyPlugin.projectSettings),
  inConfig(Client)(AssemblyPlugin.projectSettings),
  assemblyJarName in(Server, assembly) := "Server.jar",
  mainClass in(Server, assembly) := Some("udp.Server"),
  assemblyJarName in(Client, assembly) := "Client.jar",
  mainClass in(Server, assembly) := Some("udp.Client"),
  buildJars := {
    (assembly in Server).value
    (assembly in Client).value
  }
).enablePlugins(JmhPlugin).disablePlugins(AssemblyPlugin)

