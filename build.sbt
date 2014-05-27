import sbt._
import Keys._

sbtPlugin := true

name := "sbt-playi"

organization := "com.playi"

version := "1.0"


resolvers ++= Seq("Typesafe Plugin Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Era7 maven releases" at "http://releases.era7.com.s3.amazonaws.com"
)

addSbtPlugin("ohnosequences" % "sbt-s3-resolver" % "0.11.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-s3" % "0.7")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.7.1")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")

// This included to get rid of that stoopid NPE when starting sbt
libraryDependencies ++= Seq(
  "org.joda" % "joda-convert" % "1.2"
)



