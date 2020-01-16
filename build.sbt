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

version := "2.0.0"

isSnapshot := false

resolvers ++= Seq("Typesafe Plugin Repository" at "http://repo.typesafe.com/typesafe/releases/",
                  "SBT plugin releases"        at "http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/",
                  Resolver.url("sbts3 ivy resolver", url("https://dl.bintray.com/emersonloureiro/sbt-plugins"))(Resolver.ivyStylePatterns)
)

addSbtPlugin("ohnosequences" % "sbt-s3-resolver" % "0.17.0")

addSbtPlugin("cf.janga" % "sbts3" % "0.10.3")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.12")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.5.2")

// S3 Resolver settings
s3credentials := new DefaultAWSCredentialsProviderChain()

publishMavenStyle           := false

publishArtifact in Test     := false

s3region                    := Region.US_West

publishTo                   := {
  Some(s3resolver.value("Play-I S3 bucket", s3(s"playi-public-repo")).withIvyPatterns)
}

