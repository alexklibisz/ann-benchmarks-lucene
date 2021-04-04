name := "ann-benchmarks-lucene"

version := "0.1"

scalaVersion := "2.13.5"

resolvers += "Apache Snapshots" at "https://repository.apache.org/content/groups/snapshots"

libraryDependencies ++= Seq(
  // Lucene.
  "org.apache.lucene" % "lucene-core" % "9.0.0-SNAPSHOT",
  // Elasitknn.
  "com.klibisz.elastiknn" % "lucene" % "7.11.2.0",
  "com.klibisz.elastiknn" % "models" % "7.11.2.0",
  // Server.
  "com.typesafe.akka"          %% "akka-actor-typed"     % "2.6.8",
  "com.typesafe.akka"          %% "akka-stream"          % "2.6.8",
  "com.typesafe.akka"          %% "akka-http"            % "10.2.4",
  "de.heikoseeberger"          %% "akka-http-circe"      % "1.36.0",
  "com.typesafe.scala-logging" %% "scala-logging"        % "3.9.2",
  "io.circe"                   %% "circe-generic-extras" % "0.13.0",
  "ch.qos.logback"              % "logback-classic"      % "1.2.3" % Runtime,
  // Testing.
  "org.scalatest" %% "scalatest" % "3.2.5" % "test"
)
