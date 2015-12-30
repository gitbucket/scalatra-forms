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

EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource

EclipseKeys.withSource := true

publishTo := Some(Resolver.ssh("amateras-repo-scp", "shell.sourceforge.jp", "/home/groups/a/am/amateras/htdocs/mvn/") withPermissions("0664")
  as(System.getProperty("user.name"), new java.io.File(Path.userHome.absolutePath + "/.ssh/id_rsa")))
