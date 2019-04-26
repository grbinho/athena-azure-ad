name := "athena-azure-ad"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "commons-codec" % "commons-codec" % "1.10",
  "com.google.code.gson" % "gson" % "2.8.5"

)

unmanagedJars in Compile += file("lib/AthenaJDBC42_2.0.7.jar")

assemblyExcludedJars in assembly := {
  val cp = (fullClasspath in assembly).value
  cp filter { f =>
    f.data.getName.contains("AthenaJDBC")
  }
}

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

artifact in(Compile, assembly) := {
  val art = (artifact in(Compile, assembly)).value
  art.withClassifier(Some("assembly"))
}

addArtifact(artifact in(Compile, assembly), assembly)
