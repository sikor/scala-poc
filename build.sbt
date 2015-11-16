import sbtassembly.AssemblyPlugin.autoImport._
import sbt.Keys._

lazy val Server = config("Server").extend(Compile).describedAs("Blocking server compile")
lazy val Client = config("Client").extend(Compile).describedAs("Client")
lazy val QueueServer = config("QueueServer").extend(Compile).describedAs("QueueServer")
lazy val NioServer = config("NioServer").extend(Compile).describedAs("NioServer")
lazy val NioQueueServer = config("NioQueueServer").extend(Compile).describedAs("NioQueueServer")


val buildJars = taskKey[Unit]("build all jars")

lazy val root = (project in file(".")).aggregate(core, jmh)

lazy val core = (project in file("core")).settings(
  name := "core",
  version := "1.0",
  scalaVersion := "2.11.7",
  libraryDependencies ++= Seq(
    "org.monifu" %% "monifu" % "1.0-RC3",
    "org.eclipse.californium" % "californium-core" % "1.0.0-RC2",
    "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"
  ),
  dependencyOverrides += "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.4",


  inConfig(Server)(AssemblyPlugin.projectSettings),
  assemblyJarName in(Server, assembly) := "Server.jar",
  mainClass in(Server, assembly) := Some("udp.Server"),

  inConfig(Client)(AssemblyPlugin.projectSettings),
  assemblyJarName in(Client, assembly) := "Client.jar",
  mainClass in(Client, assembly) := Some("udp.Client"),

  inConfig(QueueServer)(AssemblyPlugin.projectSettings),
  assemblyJarName in(QueueServer, assembly) := "QueueServer.jar",
  mainClass in(QueueServer, assembly) := Some("udp.QueueServer"),

  inConfig(NioServer)(AssemblyPlugin.projectSettings),
  assemblyJarName in(NioServer, assembly) := "NioServer.jar",
  mainClass in(NioServer, assembly) := Some("udp.NioServer"),

  inConfig(NioQueueServer)(AssemblyPlugin.projectSettings),
  assemblyJarName in(NioQueueServer, assembly) := "NioQueueServer.jar",
  mainClass in(NioQueueServer, assembly) := Some("udp.NioQueueServer"),


  buildJars := {
    (assembly in Server).value
    (assembly in Client).value
    (assembly in QueueServer).value
    (assembly in NioServer).value
    (assembly in NioQueueServer).value
  }
).disablePlugins(JmhPlugin).disablePlugins(AssemblyPlugin)

lazy val jmh = (project in file("jmh")).settings(
  name := "jmh",
  version := "1.0",
  scalaVersion := "2.11.7",
  libraryDependencies ++= Seq(
    "org.monifu" %% "monifu" % "1.0-RC3",
    "org.eclipse.californium" % "californium-core" % "1.0.0-RC2",
    "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"
  ),
  dependencyOverrides += "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.4"
).enablePlugins(JmhPlugin).disablePlugins(AssemblyPlugin).dependsOn(core)

