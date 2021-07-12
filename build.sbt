lazy val root = (project in file("."))
  .settings(
    name := "scala-http-client",
    organization := "io.moia",
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    scmInfo := Some(ScmInfo(url("https://github.com/moia-oss/scala-http-client"), "scm:git@github.com:moia-oss/scala-http-client.git")),
    homepage := Some(url("https://github.com/moia-oss/scala-http-client")),
    scalaVersion := "2.13.6",
    crossScalaVersions := List("2.12.14", "2.13.6"),
    versionScheme := Some("early-semver"),
    scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 12)) => scalacOptions_2_12
        case Some((2, 13)) => scalacOptions_2_13
        case _             => Seq()
      }
    },
    libraryDependencies ++= akkaDependencies ++ awsDependencies ++ testDependencies ++ loggingDependencies ++ scalaDependencies
  )
  .settings(sonatypeSettings: _*)
  .configs(IntegrationTest)
  .settings(
    scalafmtOnCompile := true,
    Defaults.itSettings,
    IntegrationTest / scalacOptions := (Compile / scalacOptions).value.filterNot(_ == "-Ywarn-dead-code")
  )
  .settings(sbtGitSettings)
  .enablePlugins(
    GitVersioning,
    GitBranchPrompt
  )
  .settings(mimaSettings)

val akkaVersion     = "2.6.15"
val akkaHttpVersion = "10.2.4"

lazy val akkaDependencies = Seq(
  "com.typesafe.akka" %% "akka-stream"       % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-http"         % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-testkit"      % akkaVersion     % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test
)

lazy val awsJavaSdkVersion = "2.16.87"
lazy val awsDependencies = Seq(
  "software.amazon.awssdk" % "core" % awsJavaSdkVersion,
  "software.amazon.awssdk" % "sts"  % awsJavaSdkVersion
)

lazy val testDependencies = Seq(
  "org.scalatest"  %% "scalatest"        % "3.2.9"   % Test,
  "org.mockito"    %% "mockito-scala"    % "1.16.37" % Test,
  "org.mock-server" % "mockserver-netty" % "5.11.2"  % Test
)

lazy val loggingDependencies = Seq(
  "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.4",
  "ch.qos.logback"              % "logback-classic" % "1.2.3" % Test
)

lazy val scalaDependencies = Seq(
  "org.scala-lang.modules" %% "scala-collection-compat" % "2.5.0"
)

ThisBuild / scapegoatVersion := "1.4.9"

lazy val scalacOptions_2_12 = Seq(
  "-unchecked",
  "-deprecation",
  "-language:_",
  "-target:jvm-1.8",
  "-encoding",
  "UTF-8",
  "-Xfatal-warnings",
  "-Ywarn-unused-import",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-inaccessible",
  "-Ywarn-infer-any",
  "-Ywarn-nullary-override",
  "-Ywarn-nullary-unit"
)

lazy val scalacOptions_2_13 = Seq(
  "-unchecked",
  "-deprecation",
  "-language:_",
  "-target:jvm-1.8",
  "-encoding",
  "UTF-8",
  "-Xfatal-warnings",
  "-Xlint:strict-unsealed-patmat",
  "-Ymacro-annotations",
  "-Ywarn-dead-code"
)

lazy val sonatypeSettings = {
  import xerial.sbt.Sonatype._
  Seq(
    publishTo := sonatypePublishTo.value,
    sonatypeProfileName := organization.value,
    publishMavenStyle := true,
    sonatypeProjectHosting := Some(GitHubHosting("moia-oss", "scala-http-client", "oss-support@moia.io")),
    credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credential")
  )
}

lazy val sbtVersionRegex = "v([0-9]+.[0-9]+.[0-9]+)-?(.*)?".r

lazy val sbtGitSettings = Seq(
  git.useGitDescribe := true,
  git.baseVersion := "0.0.0",
  git.uncommittedSignifier := None,
  git.gitTagToVersionNumber := {
    case sbtVersionRegex(v, "")         => Some(v)
    case sbtVersionRegex(v, "SNAPSHOT") => Some(s"$v-SNAPSHOT")
    case sbtVersionRegex(v, s)          => Some(s"$v-$s-SNAPSHOT")
    case _                              => None
  }
)

lazy val mimaSettings = Seq(
  mimaPreviousArtifacts := Set("io.moia" %% "scala-http-client" % "4.0.0")
)
