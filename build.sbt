name := "scalatra-forms"

organization := "io.github.gitbucket"

version := "1.0.0"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra"        % "2.4.0" % "provided",
  "org.scalatra" %% "scalatra-json"   % "2.4.0" % "provided",
  "org.json4s"   %% "json4s-jackson"  % "3.3.0" % "provided",
  "org.scalatra" %% "scalatra-specs2" % "2.4.0" % "test",
  "org.eclipse.jetty" % "jetty-webapp" % "9.3.6.v20151106" % "provided",
  "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided;test"
)

publishMavenStyle := true

publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
  else                             Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>https://github.com/bizreach/elastic-scala-httpclient</url>
    <licenses>
      <license>
        <name>The Apache Software License, Version 2.0</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <url>https://github.com/bizreach/elastic-scala-httpclient</url>
      <connection>scm:git:https://github.com/bizreach/elastic-scala-httpclient.git</connection>
    </scm>
    <developers>
      <developer>
        <id>takezoe</id>
        <name>Naoki Takezoe</name>
        <email>takezoe_at_gmail.com</email>
        <timezone>+9</timezone>
      </developer>
    </developers>)

