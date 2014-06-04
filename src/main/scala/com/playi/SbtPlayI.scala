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
import sbtrelease._
import sbtrelease.ReleasePlugin._
import sbtrelease.ReleasePlugin.ReleaseKeys._
import ReleaseStateTransformations._

object SbtPlayI extends Plugin {
  import TgzAssembly.TgzAssemblyKeys._
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
      val fName = assembly.value.getName
      currBranch match {
        case "prod" =>
          Seq((new java.io.File(s"target/$fName"), s"${organization.value}/${name.value}/SHA1/$fName"),
          (new java.io.File(s"target/$fName"), s"${organization.value}/${name.value}/RELEASE/${name.value}-RELEASE.jar"))
        case "master" => //master is dev branch. less chance of errors
          Seq((new java.io.File(s"target/$fName"), s"${organization.value}/${name.value}/SNAPSHOT/${name.value}-SNAPSHOT.jar"))
        case b => throw new java.lang.IllegalArgumentException(s"the branch '$b', does not match 'master' or 'prod'.")
      }
    },
    S3.host in S3.upload := s3Repo,
    credentials += {
      val awsCreds = new DefaultAWSCredentialsProviderChain().getCredentials()
      Credentials( "Amazon S3", s3Repo, awsCreds.getAWSAccessKeyId(), awsCreds.getAWSSecretKey() )
    }
  ) ++ releaseSettings ++ Seq(
    releaseProcess := Seq[ReleaseStep](
//      runTest,                      // : ReleaseStep
      releaseTask[File](assembly),
      releaseTask[File](tgzAssembly),
      releaseTask[Unit](S3.upload)
    )
  )

  //sbtrelease.releaseTask() does not work, this does (https://github.com/sbt/sbt-release/issues/66): 
  def releaseTask[T](key: TaskKey[T]) = { st: State =>
    Project.extract(st).runTask(key, st)
    st
  }

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

  def currBranch = {
    sys.env.getOrElse("TRAVIS_BRANCH", {
      val current = """\*\s+(\w+)""".r
      "git branch --no-color".lines_!.collect { case current(name) => name }.mkString
    })
  }
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

  val buildShellPrompt = {
    (state: State) => {
      val currProject = Project.extract (state).currentRef.project
      s"[\033[36m${currProject}${scala.Console.RESET}] " +
      s"\033[32m\033[4m${SbtPlayI.currBranch}${scala.Console.RESET} > "
    }
  }
}


object TgzAssembly /*extends Plugin*/ {
  object TgzAssemblyKeys {
    lazy val tgzAssembly = taskKey[java.io.File]("packages the assembly jar into a tgz")
  }

  import TgzAssemblyKeys._

  tgzAssembly := {
    val jarFile = assembly.value
    val tgzFile = new java.io.File(s"target/${name.value}-${version.value}.tgz")
    s"tar -zpcvf ${tgzFile.getAbsolutePath} ${jarFile.getAbsolutePath}" ! match {
      case 0 => ()
      case error => sys.error(s"Error tarballing $tgzFile. Exit code: $error")
    }
    tgzFile
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
