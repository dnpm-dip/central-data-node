

name := "dnpm-ccdn"  // Central Clinical Data Node
ThisBuild / organization := "de.dnpm"
ThisBuild / scalaVersion := "3.4.2"
ThisBuild / version      := "1.0-SNAPSHOT"

ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", "services", xs @ _*) => MergeStrategy.first
  case PathList("META-INF", xs @ _*)             => MergeStrategy.discard
  case "reference.conf"                          => MergeStrategy.concat
  case x                                         => MergeStrategy.first
}


//-----------------------------------------------------------------------------
// PROJECTS
//-----------------------------------------------------------------------------

lazy val global = project
  .in(file("."))
  .settings(
    settings,
    publish / skip := true
  )
  .aggregate(
    core,
    connectors
  )


lazy val core = project
  .settings(
    name := "ccdn-core",
    settings,
    libraryDependencies ++= Seq(
      dependencies.scalatest,
      dependencies.cats_core,
      dependencies.slf4j,
      dependencies.logback,
      dependencies.play_json,
    ),
    assembly / assemblyJarName := "dnpm-ccdn-core.jar",
    assembly / mainClass       := Some("de.dnpm.ccdn.core.exec")
  )

lazy val connectors = project
  .settings(
    name := "ccdn-connectors",
    settings,
    libraryDependencies ++= Seq(
      dependencies.scalatest,
      dependencies.play_ahc,
      dependencies.play_ahc_js,
    ),
    assembly / assemblyJarName := "dnpm-ccdn-connectors.jar",
  )
  .dependsOn(
    core
  )


//-----------------------------------------------------------------------------
// DEPENDENCIES
//-----------------------------------------------------------------------------

lazy val dependencies =
  new {
    val scalatest   = "org.scalatest"     %% "scalatest"               % "3.2.18" % Test
    val slf4j       = "org.slf4j"         %  "slf4j-api"               % "2.0.13"
    val logback     = "ch.qos.logback"    %  "logback-classic"         % "1.5.6"
    val cats_core   = "org.typelevel"     %% "cats-core"               % "2.12.0"
    val play_json   = "org.playframework" %% "play-json"               % "3.0.3"
    val play_ahc    = "org.playframework" %% "play-ahc-ws-standalone"  % "3.0.5"
    val play_ahc_js = "org.playframework" %% "play-ws-standalone-json" % "3.0.5"
  }


//-----------------------------------------------------------------------------
// SETTINGS
//-----------------------------------------------------------------------------

lazy val settings = commonSettings

lazy val compilerOptions = Seq(
  "-encoding", "utf8",
  "-unchecked",
  "-Xfatal-warnings",
  "-feature",
  "-language:higherKinds",
  "-language:postfixOps",
  "-deprecation"
)

lazy val commonSettings = Seq(
  scalacOptions ++= compilerOptions,
  resolvers ++= 
    Seq("Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository") ++
    Resolver.sonatypeOssRepos("releases") ++
    Resolver.sonatypeOssRepos("snapshots")
)

