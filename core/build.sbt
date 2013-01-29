organization := "com.libitec"

name := "sce.core-eventsourced"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.10.0"

resolvers += "Eligosource Releases" at "http://repo.eligotech.com/nexus/content/repositories/eligosource-releases"

resolvers += "Eligosource Snapshots" at "http://repo.eligotech.com/nexus/content/repositories/eligosource-snapshots"

libraryDependencies ++= Seq(
    "org.eligosource" %% "eventsourced" % "0.5-SNAPSHOT"
)