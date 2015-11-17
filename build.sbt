name := "scalatra-forms"

organization := "jp.sf.amateras"

version := "0.2.0"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra"        % "2.3.0" % "provided",
  "org.scalatra" %% "scalatra-json"   % "2.3.0" % "provided",
  "org.json4s"   %% "json4s-jackson"  % "3.2.9" % "provided",
  "org.scalatra" %% "scalatra-specs2" % "2.3.0" % "test",
  "org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "provided",
  "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
)

EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource

EclipseKeys.withSource := true

publishTo := Some(Resolver.ssh("amateras-repo-scp", "shell.sourceforge.jp", "/home/groups/a/am/amateras/htdocs/mvn/") withPermissions("0664")
  as(System.getProperty("user.name"), new java.io.File(Path.userHome.absolutePath + "/.ssh/id_rsa")))
