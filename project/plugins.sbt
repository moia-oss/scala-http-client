// adds reStart and reStop
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

// adds scalafmt
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.2")

// adds dependencyCheck
addSbtPlugin("net.vonbuchholtz" % "sbt-dependency-check" % "2.0.0")

// sbt> scapegoat
addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.1.0")

// Publish to sonatype
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.8.1")

// publishSigned
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")
