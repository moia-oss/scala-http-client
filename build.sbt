lazy val root = (project in file("."))
  .settings(
    name := "scala-http-client",
    organization := "io.moia",
    version := "1.3.1",
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    scmInfo := Some(ScmInfo(url("https://github.com/moia-dev/scala-http-client"), "scm:git@github.com:moia-dev/scala-http-client.git")),
    homepage := Some(url("https://github.com/moia-dev/scala-http-client")),
    scalaVersion := "2.13.1",
    crossScalaVersions := List("2.13.1", "2.12.10"),
    scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 12)) => scalacOptions_2_12
        case Some((2, 13)) => scalacOptions_2_13
        case _             => Seq()
      }
    },
    libraryDependencies ++= akkaDependencies ++ awsDependencies ++ testDependencies ++ loggingDependencies
  )
  .settings(sonatypeSettings: _*)
  .configs(IntegrationTest)
  .settings(
    scalafmtOnCompile := true,
    Defaults.itSettings,
    scalacOptions in IntegrationTest := (scalacOptions in Compile).value.filterNot(_ == "-Ywarn-dead-code")
  )

val akkaVersion     = "2.5.31"
val akkaHttpVersion = "10.1.11"

lazy val akkaDependencies = Seq(
  "com.typesafe.akka" %% "akka-stream"       % akkaVersion,
  "com.typesafe.akka" %% "akka-http"         % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-testkit"      % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test
)

lazy val awsJavaSdkVersion = "1.11.759"
lazy val awsDependencies = Seq(
  "com.amazonaws" % "aws-java-sdk-core" % awsJavaSdkVersion,
  "com.amazonaws" % "aws-java-sdk-sts"  % awsJavaSdkVersion
)

lazy val testDependencies = Seq(
  "org.scalatest"   %% "scalatest"       % "3.1.1"  % Test,
  "org.mockito"     %% "mockito-scala"   % "1.13.6" % Test,
  "org.mock-server" % "mockserver-netty" % "5.10.0"  % Test
)

lazy val loggingDependencies = Seq(
  "com.typesafe.scala-logging" %% "scala-logging"  % "3.9.2",
  "ch.qos.logback"             % "logback-classic" % "1.2.3" % Test
)

scapegoatVersion in ThisBuild := "1.4.1"

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
  "-Ywarn-dead-code",
  "-Ymacro-annotations"
)

lazy val sonatypeSettings = {
  import xerial.sbt.Sonatype._
  Seq(
    publishTo := sonatypePublishTo.value,
    sonatypeProfileName := organization.value,
    publishMavenStyle := true,
    sonatypeProjectHosting := Some(GitHubHosting("moia-dev", "scala-http-client", "oss-support@moia.io")),
    credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credential")
  )
}
