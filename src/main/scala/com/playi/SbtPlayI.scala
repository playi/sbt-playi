package com.playi

import sbt._
import Keys._
import com.amazonaws.auth._
import com.amazonaws.services.s3.model.Region
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain

object SbtPlayI extends Plugin {

  val coreBuildSettings = Seq(
    organization := "com.playi",
    organizationName := "com.playi",
    version := PlayIUtil.getSHA(),
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
    )
  )

  override def projectSettings = 
    coreBuildSettings       ++ 
    PlayIAssembly.settings  ++ 
    Resolvers.settings      ++ 
    PlayIS3Upload.settings  ++ 
    PlayIRelease.settings   ++
    net.virtualvoid.sbt.graph.Plugin.graphSettings
}



object PlayIUtil {
  // ---
  // Helper Functions
  // ---
  object devnull extends ProcessLogger {
    def info (s: => String) {}
    def error (s: => String) { }
    def buffer[T] (f: => T): T = f
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

  def getSHA(): String = ("git log --format='%H' -n 1" lines_! devnull headOption) getOrElse "-" replaceAll("'","")

  def currBranch = {
    sys.env.getOrElse("TRAVIS_BRANCH", {
      val current = """\*\s+(\w+)""".r
      "git branch --no-color".lines_!.collect { case current(name) => name }.mkString
    })
  }

}


/********************************************************************
*   Configures the Release settings 
********************************************************************/
object PlayIRelease {

  import sbtrelease._
  import sbtrelease.ReleaseStep
  import sbtrelease.ReleasePlugin._
  import sbtrelease.ReleasePlugin.ReleaseKeys._
  import ReleaseStateTransformations._
  import sbtassembly.Plugin.AssemblyKeys._
  import com.typesafe.sbt.S3Plugin._

  //sbtrelease.releaseTask() does not work, this does (https://github.com/sbt/sbt-release/issues/66): 
  def releaseTask[T](key: TaskKey[T]) = { state: State =>
    Project.extract(state).runTask(key, state)
    state
  }

  val releaseSteps = Seq[ReleaseStep](
    //      runTest,                      // : ReleaseStep
    releaseTask[File](assembly),
    releaseTask[Unit](S3.upload)
  )

  val settings = releaseSettings ++ Seq(
    releaseProcess := releaseSteps
  )
}


/********************************************************************
*   Configures the ShellPrompt inside sbt/activator
********************************************************************/
object ShellPrompt {

  val buildShellPrompt = {
    (state: State) => {
      val currProject = Project.extract (state).currentRef.project
      s"[\033[36m${currProject}${scala.Console.RESET}] " +
      s"\033[32m\033[4m${PlayIUtil.currBranch}${scala.Console.RESET} > "
    }
  }
}



/********************************************************************
*   Define all the places we look for artifacts ie our resolvers
********************************************************************/
object Resolvers {

  import ohnosequences.sbt._
  import ohnosequences.sbt.SbtS3Resolver._

  val settings = S3Resolver.defaults ++ Seq(
    // S3 Resolver settings
    s3credentials := new DefaultAWSCredentialsProviderChain(),
    isSnapshot                  := true,
    publishMavenStyle           := false,
    publishArtifact in Test     := false,
    s3region                    := Region.US_West,
    publishTo                   := {
      val target = if(isSnapshot.value) "snapshots" else "releases"
      Some(s3resolver.value("Play-I S3 bucket", s3(s"playi-$target")).withIvyPatterns)
    }
  )

  //val sunrepo           = "Sun Maven2 Repo"       at "http://download.java.net/maven/2"
  val typesafeReleases  = "Typesafe Releases"     at "http://repo.typesafe.com/typesafe/releases/"
  val typesafeSnapshots = "Typesafe Snapshots"    at "http://repo.typesafe.com/typesafe/snapshots/"
  val mavenCentral      = "Maven Central"         at "http://repo1.maven.org/maven2"

  val publicResolvers = Seq(mavenCentral, typesafeReleases, typesafeSnapshots)

  val playIReleases: Def.Initialize[Resolver] = Def.setting {
    toSbtResolver( s3resolver.value("PlayI Releases", s3("playi-releases")).withIvyPatterns )
  }

  val playISnapshots: Def.Initialize[Resolver] = Def.setting {
    toSbtResolver( s3resolver.value("PlayI Snapshots", s3("playi-snapshots")).withIvyPatterns )
  }
}




/********************************************************************
*   Setup the default settings for the assembly plugin
********************************************************************/
object PlayIAssembly {
  import sbtassembly.Plugin.AssemblyKeys._
  import sbtassembly.Plugin.assemblySettings
  import sbtassembly.Plugin.MergeStrategy

  val settings = assemblySettings ++ Seq(
    jarName in assembly := s"${name.value}-${version.value}.jar",
    mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
      {
        case "application.conf"                => MergeStrategy.concat
        case "logback.xml" | "logger.xml"      => MergeStrategy.discard
        case x => old(x)
      }
    }
  )
}


/********************************************************************
*   Configuration for our S3 upload tasks
********************************************************************/
object PlayIS3Upload {

  import com.typesafe.sbt.S3Plugin._
  import sbtassembly.Plugin.AssemblyKeys._

  val s3Repo = "playi-repo.s3.amazonaws.com"
  val branch = PlayIUtil.currBranch
  val coreSettings = s3Settings ++ Seq(
    S3.progress in S3.upload := true,
    S3.host in S3.upload := s3Repo,
    credentials += {
      val awsCreds = new DefaultAWSCredentialsProviderChain().getCredentials()
      Credentials( "Amazon S3", s3Repo, awsCreds.getAWSAccessKeyId(), awsCreds.getAWSSecretKey() )
    }
  )


  val prodSettings = coreSettings ++ Seq(
    mappings in S3.upload := {
      val fName = assembly.value.getName
      Seq((new java.io.File(s"target/$fName"), s"${organization.value}/${name.value}/SHA1/$fName"),
      (new java.io.File(s"target/$fName"), s"${organization.value}/${name.value}/RELEASE/${name.value}-RELEASE.jar"))
    }
  )

  val masterSettings = coreSettings ++ Seq(
    mappings in S3.upload := {
      val fName = assembly.value.getName
      Seq((new java.io.File(s"target/$fName"), s"${organization.value}/${name.value}/SNAPSHOT/${name.value}-SNAPSHOT.jar"))
    }
  )

  def defaultSettings(branchName: String) = Seq(
      S3.upload := {
        val log = streams.value.log
        log.info(s"Skipping s3Upload because build is running against branch: $branchName")
      }
    )




  val settings = branch match {
    case "prod"   => prodSettings 
    case "master" => masterSettings
    case branch   => defaultSettings(branch)
  }
}
