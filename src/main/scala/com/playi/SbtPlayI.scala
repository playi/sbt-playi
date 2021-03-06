package com.playi

import sbt._
import sbt.Keys._
import com.amazonaws.auth._
import com.amazonaws.services.s3.model.Region
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.typesafe.sbt.SbtNativePackager._
import scala.sys.process._

object SbtPlayI extends AutoPlugin {

  lazy val coreBuildSettings = Seq(
    organization := "com.playi",
    organizationName := "com.playi",
    version := PlayIUtil.getSHA(),
    crossPaths := false,
    shellPrompt  := ShellPrompt.buildShellPrompt,
    resolvers ++= Resolvers.publicResolvers ++ Seq(Resolvers.playIReleases.value, Resolvers.playISnapshots.value),
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

  def PlayISettings =
    coreBuildSettings       ++
    PlayIBuildInfo.settings ++
    Resolvers.settings      ++
    LogbackProperties.settings
}



object PlayIUtil {
  // ---
  // Helper Functions
  // ---
  object devnull extends ProcessLogger {
    def info (s: => String) {}
    def error (s: => String) { }
    def buffer[T] (f: => T): T = f
    def err(s: => String): Unit = { }
    def out(s: => String): Unit = { }
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

  def getSHA(): String = ("git log --format='%H' -n 1" lineStream_! devnull headOption) getOrElse "-" replaceAll("'","")

  def currBranch = {
    Seq("TRAVIS_BRANCH", "GIT_BRANCH").flatMap(sys.env.get).headOption.getOrElse({
      val current = """\*\s+(\w+)""".r
      "git branch --no-color".lineStream_!.collect { case current(name) => name }.mkString
    }).split("/").toSeq.lastOption.getOrElse("")
  }

}

/********************************************************************
*   Configuration for creating a build-info file
********************************************************************/
object PlayIBuildInfo {
  import java.io._
  import java.util._
  import java.text._

  val buildInfoFile = settingKey[String]("Where to write the build info")
  val buildInfoFileSetting = buildInfoFile := "src/main/resources/buildInfo.conf"

  val buildInfo = taskKey[File]("A sample string task.")
  val buildInfoTask = buildInfo := {
    val sha = PlayIUtil.getSHA()
    val branch = PlayIUtil.currBranch
    val buildDate = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z").format(new Date)
    val buildHost = java.net.InetAddress.getLocalHost().getHostName()
    val buildUser = System.getProperty("user.name", "????")

    val file = new File(buildInfoFile.value)
    val dir = file.getParentFile()
    if(!dir.exists())
      dir.mkdirs()

    if(file.exists())
      file.delete()
    file.createNewFile()

    val writer = new PrintWriter(file)
    writer.write(s"""playi.buildInfo = {
        |   buildDate = "$buildDate"
        |   buildHost = "$buildHost"
        |   buildUser = "$buildUser"
        |   branch = "$branch"
        |   commit = "$sha"
        | }
        """.stripMargin
    )
    writer.close()
    file
  }

  val settings: sbt.Def.SettingsDefinition = Seq(buildInfoFileSetting, buildInfoTask)
}

/********************************************************************
*   Configuration for creating a logback.properties file
********************************************************************/
object LogbackProperties {
  import java.io._
  import java.util._
  import java.text._

  val logbackPropsFile = settingKey[String]("Where to write the logback.properties file")
  val logbackPropsFileSetting = logbackPropsFile := "src/main/resources/logback.properties"

  val logbackProps = taskKey[File]("A sample string task.")
  val logbackPropsTask = logbackProps := {
    val sha = PlayIUtil.getSHA()
    val branch = PlayIUtil.currBranch

    val file = new File(logbackPropsFile.value)
    val dir = file.getParentFile()
    if(!dir.exists())
      dir.mkdirs()

    if(file.exists())
      file.delete()
    file.createNewFile()

    val writer = new PrintWriter(file)
    writer.write(s"""
        | serviceName = ${name.value}
        """.stripMargin
    )
    writer.close()
    file
  }

  val settings: sbt.Def.SettingsDefinition = Seq(logbackPropsFileSetting, logbackPropsTask)
}

/********************************************************************
*   Configures the Release settings
********************************************************************/
object PlayIRelease {

  import sbtrelease._
  import sbtrelease.ReleasePlugin._
  import sbtrelease.ReleasePlugin.autoImport._
  import sbtrelease.ReleasePlugin.autoImport.ReleaseKeys._
  import ReleaseStateTransformations._
  import com.typesafe.sbt.S3Plugin._
  import com.typesafe.sbt.SbtNativePackager._
  import com.typesafe.sbt.SbtNativePackager.autoImport._
  import com.typesafe.sbt.packager.Keys.packageZipTarball
  import com.typesafe.sbt.S3Plugin._
  import com.typesafe.sbt.S3Plugin.autoImport._

  //sbtrelease.releaseTask() does not work, this does (https://github.com/sbt/sbt-release/issues/66):
  def releaseTask[T](key: TaskKey[T]) = { state: State =>
    Project.extract(state).runTask(key, state)
    state
  }

  lazy val releaseSteps = Seq[ReleaseStep](
    //      runTest,                      // : ReleaseStep
    releaseTask[File](PlayIBuildInfo.buildInfo),
    releaseTask[File](LogbackProperties.logbackProps),
    releaseTask[File](packageZipTarball in Universal),
    releaseTask[Seq[String]](s3Upload)
  )

  lazy val settings = ReleasePlugin.projectSettings ++ Seq(
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
  import ohnosequences.sbt.SbtS3Resolver.autoImport._

  val settings = projectSettings ++ Seq(
    // S3 Resolver settings
    s3credentials := new DefaultAWSCredentialsProviderChain(),
    isSnapshot                  := true,
    publishMavenStyle           := false,
    publishArtifact in Test     := false,
    s3region                    := Region.EU_Ireland,
    publishTo                   := {
      val target = if(isSnapshot.value) "snapshots" else "releases"
      Some(s3resolver.value("Play-I S3 bucket", s3(s"playi-$target")).withIvyPatterns)
    }
  )

  //val sunrepo           = "Sun Maven2 Repo"       at "http://download.java.net/maven/2"
  val typesafeReleases  = "Typesafe Releases"     at "https://repo.typesafe.com/typesafe/releases/"
  val typesafeSnapshots = "Typesafe Snapshots"    at "https://repo.typesafe.com/typesafe/snapshots/"
  val mavenCentral      = "Maven Central"         at "https://repo1.maven.org/maven2"
  val jcenterRepo       = "jcenter"               at "https://jcenter.bintray.com/"

  val sonatypeSnapshots = "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  val sonatypeReleases  = "Sonatype OSS Releases"  at "https://oss.sonatype.org/content/repositories/releases"

  val publicResolvers = Seq(
    mavenCentral,
    typesafeReleases,
    typesafeSnapshots,
    sonatypeReleases,
    sonatypeSnapshots,
    jcenterRepo
  )

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
/*
object PlayIAssembly {
  import sbtassembly.Plugin.AssemblyKeys._
  import sbtassembly.Plugin.assemblySettings
  import sbtassembly.Plugin.MergeStrategy

  lazy val settings = assemblySettings ++ Seq(
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
*/


/********************************************************************
*   Configuration for our S3 upload tasks
********************************************************************/
object PlayIS3Upload {

  import com.typesafe.sbt.S3Plugin._
  import com.typesafe.sbt.S3Plugin.autoImport._
  import com.typesafe.sbt.packager.Keys.packageZipTarball

  val s3Repo = "playi-repo.s3.amazonaws.com"
  val branch = PlayIUtil.currBranch

  lazy val coreSettings = projectSettings ++ Seq(
    s3Progress in s3Upload := true,
    s3Host in s3Upload := s3Repo,
    credentials += {
      val awsCreds = new DefaultAWSCredentialsProviderChain().getCredentials()
      Credentials( "Amazon S3", s3Repo, awsCreds.getAWSAccessKeyId(), awsCreds.getAWSSecretKey() )
    }
  )

  lazy val prodSettings = coreSettings ++ Seq(
    mappings in s3Upload := {
      val tgzFile = (packageZipTarball in Universal).value
      val fName = tgzFile.getName()
      val sha = PlayIUtil.getSHA()
      Seq((tgzFile, s"${organization.value}/${name.value}/$sha/$fName"),
      (tgzFile, s"${organization.value}/${name.value}/RELEASE/${name.value}-RELEASE.tgz"))
    }
  )

  lazy val masterSettings = coreSettings ++ Seq(
    mappings in s3Upload := {
      val log = streams.value.log
      val tgzFile = (packageZipTarball in Universal).value
      val fName = tgzFile.getName()
      Seq((tgzFile, s"${organization.value}/${name.value}/SNAPSHOT/${name.value}-SNAPSHOT.tgz"))
    }
  )

  def defaultSettings(branchName: String) = Seq(
      s3Upload := {
        val log = streams.value.log
        log.info(s"Skipping s3Upload because build is running against branch: $branchName")
        Seq()
      }
    )


  lazy val settings = branch match {
    case "prod"   => prodSettings
    case "master" => masterSettings
    case "alpha"  => masterSettings
    case branch   => defaultSettings(branch)
  }
}
