import sbt._
import Keys._

object SceBuild extends Build {

  lazy val root          = Project(id = "front", base = file(".")) aggregate(core)
  lazy val core          = Project(id = "core", base = file("core"))
}
