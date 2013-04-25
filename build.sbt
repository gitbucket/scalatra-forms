name := "scalatra-forms"

organization := "jp.sf.amateras"

version := "0.0.1"

scalaVersion := "2.10.1"

libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra"        % "2.2.0",
  "org.scalatra" %% "scalatra-specs2" % "2.2.0" % "test",
  "org.scalatra" %% "scalatra-json"   % "2.2.0",
  "org.json4s"   %% "json4s-jackson"  % "3.2.4",
  "org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "provided",
  "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
)

EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource

EclipseKeys.withSource := true
