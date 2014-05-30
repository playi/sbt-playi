package com.playi

import sbt._
import Keys._
import sbtassembly.Plugin.AssemblyKeys._
import sbtassembly.Plugin.assemblySettings
import sbtassembly.Plugin.MergeStrategy
import com.amazonaws.auth._
import com.amazonaws.services.s3.model.Region
import ohnosequences.sbt._
import ohnosequences.sbt.SbtS3Resolver._
import com.typesafe.sbt.S3Plugin._
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain


object SbtPlayI extends Plugin {
  val s3Repo = "playi-repo.s3.amazonaws.com"

  override def projectSettings = S3Resolver.defaults ++ assemblySettings ++ Seq(
    organization := "com.playi",
    organizationName := "com.playi",
    version := getSHA(),
    crossPaths := false,
    shellPrompt  := ShellPrompt.buildShellPrompt,
    resolvers := Resolvers.publicResolvers ++ Seq(Resolvers.playIReleases.value, Resolvers.playISnapshots.value),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-Xlint",
      "-Ywarn-dead-code",
      "-language:_",
      "-target:jvm-1.7",
      "-encoding", "UTF-8"
    ),

    //sbt-assembly settings
    mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
      {
        case "application.conf"                => MergeStrategy.concat
        case "logback.xml" | "logger.xml"      => MergeStrategy.discard
        case x => old(x)
      }
    },

    // S3 Resolver settings
    s3credentials := new DefaultAWSCredentialsProviderChain(),
    isSnapshot                  := true,
    publishMavenStyle           := false,
    publishArtifact in Test     := false,
    s3region                    := Region.US_West,
    publishTo                   := {
      val target = if(isSnapshot.value) "snapshots" else "releases"
      Some(s3resolver.value("Play-I S3 bucket", s3(s"playi-$target")).withIvyPatterns)
    },
    jarName in assembly := s"${name.value}-${version.value}.jar"
  ) ++ s3Settings ++ Seq(
    S3.progress in S3.upload := true,
    mappings in S3.upload := {
      val fName = jarName.value
      Seq((new java.io.File(s"target/$fName"), s"${organization.value}/${name.value}/${version.value}/$fName"))
    },
    S3.host in S3.upload := s3Repo,
    credentials += {
      val awsCreds = new DefaultAWSCredentialsProviderChain().getCredentials()
      Credentials( "Amazon S3", s3Repo, awsCreds.getAWSAccessKeyId(), awsCreds.getAWSSecretKey() )
    }
  )


  def addSnapshot(versionStr: String): String = {
    import java.util.Calendar
    val c = Calendar.getInstance()
    val dateStr = "%d%d02%d02%d02%d02%d02.%d".format(
        c.get(Calendar.YEAR),
        c.get(Calendar.MONTH),
        c.get(Calendar.DATE),
        c.get(Calendar.HOUR),
        c.get(Calendar.MINUTE),
        c.get(Calendar.SECOND),
        c.get(Calendar.MILLISECOND)
    )

    versionStr + dateStr
  }

  object devnull extends ProcessLogger {
    def info (s: => String) {}
    def error (s: => String) { }
    def buffer[T] (f: => T): T = f
  }

  def getSHA(): String = ("git log --format='%H' -n 1" lines_! devnull headOption) getOrElse "-" replaceAll("'","")
}

/********************************************************************
*   Configures the ShellPrompt inside sbt/activator
********************************************************************/
object ShellPrompt {
  object devnull extends ProcessLogger {
    def info (s: => String) {}
    def error (s: => String) { }
    def buffer[T] (f: => T): T = f
  }

  def currBranch = (
    ("git branch" lines_! devnull headOption)
      getOrElse "-" stripPrefix "* "
  )

  val buildShellPrompt = {
    (state: State) => {
      val currProject = Project.extract (state).currentRef.project
      s"[\033[36m${currProject}${scala.Console.RESET}] " +
      s"\033[32m\033[4m${currBranch}${scala.Console.RESET} > "
    }
  }
}




/********************************************************************
*   Define all the places we look for artifacts ie our resolvers
********************************************************************/
object Resolvers {
  //val sunrepo           = "Sun Maven2 Repo"       at "http://download.java.net/maven/2"
  val typesafeReleases  = "Typesafe Releases"     at "http://repo.typesafe.com/typesafe/releases/"
  val typesafeSnapshots = "Typesafe Snapshots"    at "http://repo.typesafe.com/typesafe/snapshots/"
  val mavenCentral      = "Maven Central"         at "http://repo1.maven.org/maven2"

  val publicResolvers = Seq(typesafeReleases, typesafeSnapshots, mavenCentral)

  val playIReleases: Def.Initialize[Resolver] = Def.setting {
    toSbtResolver( s3resolver.value("PlayI Releases", s3("playi-releases")).withIvyPatterns )
  }

  val playISnapshots: Def.Initialize[Resolver] = Def.setting {
    toSbtResolver( s3resolver.value("PlayI Snapshots", s3("playi-snapshots")).withIvyPatterns )
  }
}
