addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "1.5.1")

addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.5.1")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.9")

resolvers ++= Seq(
  "Scala Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  Classpaths.sbtPluginReleases
)

resolvers ++= Seq(
  "Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/"
)

resolvers ++= Seq(
  Resolver.url("typesafe-ivy-repo", url("http://typesafe.artifactoryonline.com/typesafe/releases"))(Resolver.ivyStylePatterns),
  "spray repo" at "http://repo.spray.io/"
)