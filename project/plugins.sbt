// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository 
resolvers += "Typesafe Plugin Repository" at "https://repo.typesafe.com/typesafe/releases/"

// The next two lines allow us to use S3 as a repo
resolvers += Resolver.jcenterRepo

addSbtPlugin("ohnosequences" % "sbt-s3-resolver" % "0.17.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.2.2")
