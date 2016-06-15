import sbt.Keys._
import sbtprotobuf.{ProtobufPlugin=>PB}

scalaVersion in ThisBuild := "2.12.0-M4"

lazy val protocol = project.in(file("protocol"))
  .settings(commonSettings)

lazy val analytics = project.in(file("analytics"))
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(serverSettings)
  .settings(protobufSettings(protocol))

lazy val analyticsUi = project.in(file("analytics-ui"))
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(serverSettings)
  .settings(protobufSettings(protocol))
lazy val ingest = project.in(file("ingest"))
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(serverSettings)
  .settings(protobufSettings(protocol))

lazy val commonSettings = Seq(
  organization := "org.eigengo",
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")
)

def protobufSettings(protocol: Project) = PB.protobufSettings ++ Seq(
  generatedTargets in PB.protobufConfig <++= (sourceDirectory in Compile){ dir =>
    Seq((dir / ".." / "generated" / "scala", "*.scala"))
  },
  version in PB.protobufConfig := "2.6.1",
  PB.runProtoc in PB.protobufConfig := (args => com.github.os72.protocjar.Protoc.runProtoc("-v261" +: args.toArray)),
  sourceDirectories in PB.protobufConfig := ((resourceDirectories in protocol) in Compile).value,
  externalIncludePath in PB.protobufConfig := ((resourceDirectory in protocol) in Compile).value,
  libraryDependencies in PB.protobufConfig += (projectID in protocol).value,
  libraryDependencies += "com.google.protobuf" % "protobuf-java" % (version in PB.protobufConfig).value % PB.protobufConfig.name
)

lazy val dockerSettings = Seq(
  dockerBaseImage := "cakesolutions/alpine-dcos-base:latest",
  dockerUpdateLatest := true,
  dockerRepository := Some("eigengo"),
  packageName in Docker := s"rs16-${name.value}",
  maintainer in Docker := "Eigengo <state@eigengo.org>",
  version in Docker := sys.props.getOrElse("tag", default = version.value),
  daemonUser in Docker := "root"
)

lazy val serverSettings = Seq(
  parallelExecution in Test := false,
  testGrouping in Test <<= definedTests in Test map singleTests
)

/*
 * This definition and the server settings are based on those in
 * https://github.com/akka/akka-persistence-cassandra/blob/v0.11/build.sbt
 *
 * This is just so that each test suite that makes use of Cassandra is started in its own JVM. This is required
 * because Cassandra can only be started once per JVM. We'll actually end up running *every* test suite in its own
 * JVM, but that's OK.
 */
def singleTests(tests: Seq[TestDefinition]) = tests.map { test =>
  Tests.Group(
    name = test.name,
    tests = Seq(test),
    runPolicy = Tests.SubProcess(ForkOptions(runJVMOptions = Seq("-Xms512M", "-Xmx1G"))))
}