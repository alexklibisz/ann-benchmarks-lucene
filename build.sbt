name := "ann-benchmarks-lucene"

version := "0.0.4"

scalaVersion := "2.13.5"

resolvers += "Apache Snapshots" at "https://repository.apache.org/content/groups/snapshots"

val akkaHttpVersion = "10.2.4"
val akkaVersion     = "2.6.8"

libraryDependencies ++= Seq(
  // Lucene.
  "org.apache.lucene" % "lucene-core" % "9.0.0-SNAPSHOT",
  // Elasitknn.
  "com.klibisz.elastiknn" % "lucene" % "7.11.2.0",
  "com.klibisz.elastiknn" % "models" % "7.11.2.0",
  // Server.
  "com.typesafe.akka"          %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka"          %% "akka-stream"      % akkaVersion,
  "com.typesafe.akka"          %% "akka-http"        % akkaHttpVersion,
  "de.heikoseeberger"          %% "akka-http-circe"  % "1.36.0",
  "com.typesafe.scala-logging" %% "scala-logging"    % "3.9.2",
  "io.circe"                   %% "circe-generic"    % "0.13.0",
  "ch.qos.logback"              % "logback-classic"  % "1.2.3" % Runtime,
  "commons-io"                  % "commons-io"       % "2.8.0",
  // Testing.
  "com.typesafe.akka" %% "akka-testkit"      % akkaVersion     % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "org.scalatest"     %% "scalatest"         % "3.2.5"         % Test
)

javaOptions in Test ++= Seq("-Xms3G", "-Xmx3G")
fork in Test := true
testOptions in Test += Tests.Argument("-oD")

test in assembly := {}
assemblyJarName in assembly := s"${name.value}.jar"
