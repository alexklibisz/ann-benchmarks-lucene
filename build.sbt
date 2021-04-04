name := "ann-benchmarks-lucene"

version := "0.1"

scalaVersion := "2.13.5"

resolvers += "Apache Snapshots" at "https://repository.apache.org/content/groups/snapshots"

libraryDependencies ++= Seq(
  "org.apache.lucene"     % "lucene-core" % "9.0.0-SNAPSHOT",
  "com.klibisz.elastiknn" % "lucene"      % "7.11.2.0",
  "com.klibisz.elastiknn" % "models"      % "7.11.2.0",
  "org.scalatest"        %% "scalatest"   % "3.2.5" % "test"
)
