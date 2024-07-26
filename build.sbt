

name := "dnpm-ccdn"  // Central Clinical Data Node
ThisBuild / organization := "de.dnpm"
ThisBuild / scalaVersion := "3.4.2"
ThisBuild / version      := "1.0-SNAPSHOT"


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
    )
  )

/*
lazy val api = project
  .settings(
    name := "pan-app-api",
    settings,
    libraryDependencies ++= Seq(
      dependencies.scalatest,
    )
  )
  .dependsOn(
    core
  )

lazy val impl = project
  .settings(
    name := "pan-app-impl",
    settings,
    libraryDependencies ++= Seq(
      dependencies.scalatest,
    )
  )
  .dependsOn(
    api
  )
*/


//-----------------------------------------------------------------------------
// DEPENDENCIES
//-----------------------------------------------------------------------------

lazy val dependencies =
  new {
    val scalatest  = "org.scalatest"      %% "scalatest"       % "3.2.18" % Test
    val slf4j      = "org.slf4j"          %  "slf4j-api"       % "2.0.13"
    val logback    = "ch.qos.logback"     %  "logback-classic" % "1.5.6" % Test
    val cats_core  = "org.typelevel"      %% "cats-core"       % "2.12.0"
    val play_json  = "org.playframework"  %% "play-json"       % "3.0.3"
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

