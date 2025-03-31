

name := "dnpm-ccdn"  // Central Clinical Data Node
ThisBuild / organization := "de.dnpm"
ThisBuild / scalaVersion := "2.13.16"
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
      dependencies.mtb_dtos,
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
    val mtb_dtos    = "de.dnpm.dip"       %% "mtb-dto-model"           % "1.0-SNAPSHOT"
  }


//-----------------------------------------------------------------------------
// SETTINGS
//-----------------------------------------------------------------------------

lazy val settings = commonSettings

// Compiler options from: https://alexn.org/blog/2020/05/26/scala-fatal-warnings/
lazy val compilerOptions = Seq(
  // Feature options
  "-encoding", "utf-8",
  "-explaintypes",
  "-feature",
  "-language:existentials",
  "-language:experimental.macros",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-Ymacro-annotations",

  // Warnings as errors!
  "-Xfatal-warnings",

  // Linting options
  "-unchecked",
  "-Xcheckinit",
  "-Xlint:adapted-args",
  "-Xlint:constant",
  "-Xlint:delayedinit-select",
  "-Xlint:deprecation",
  "-Xlint:doc-detached",
  "-Xlint:inaccessible",
  "-Xlint:infer-any",
  "-Xlint:missing-interpolator",
  "-Xlint:nullary-unit",
  "-Xlint:option-implicit",
  "-Xlint:package-object-classes",
  "-Xlint:poly-implicit-overload",
  "-Xlint:private-shadow",
  "-Xlint:stars-align",
  "-Xlint:type-parameter-shadow",
  "-Wdead-code",
  "-Wextra-implicit",
  "-Wnumeric-widen",
  "-Wunused:imports",
  "-Wunused:locals",
  "-Wunused:patvars",
  "-Wunused:privates",
  "-Wunused:implicits",
  "-Wvalue-discard",

  // Deactivated to avoid many false positives from 'evidence' parameters in context bounds
//  "-Wunused:params",
)


lazy val commonSettings = Seq(
  scalacOptions ++= compilerOptions,
  resolvers ++= 
    Seq("Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository") ++
    Resolver.sonatypeOssRepos("releases") ++
    Resolver.sonatypeOssRepos("snapshots")
)

