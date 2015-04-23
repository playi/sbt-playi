import sbt._
import Keys._
import com.amazonaws.services.s3.model.Region
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import ohnosequences.sbt._
import ohnosequences.sbt.SbtS3Resolver._
import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._


sbtPlugin := true

name := "sbt-playi"

organization := "com.playi"

version := "1.0"

isSnapshot := false

resolvers ++= Seq("Typesafe Plugin Repository" at "http://repo.typesafe.com/typesafe/releases/",
                  "Era7 maven releases"        at "http://releases.era7.com.s3.amazonaws.com",
                  "SBT plugin releases"        at "http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"
)

addSbtPlugin("ohnosequences" % "sbt-s3-resolver" % "0.11.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-s3" % "0.7")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.8.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.7.5")

S3Resolver.defaults

// S3 Resolver settings
s3credentials := new DefaultAWSCredentialsProviderChain()

publishMavenStyle           := false

publishArtifact in Test     := false

s3region                    := Region.US_West

publishTo                   := {
  Some(s3resolver.value("Play-I S3 bucket", s3(s"playi-public-repo")).withIvyPatterns)
}

