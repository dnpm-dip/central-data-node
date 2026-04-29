import scala.util.Properties.envOrElse


name := "dnpm-ccdn"  // Central Clinical Data Node
ThisBuild / organization := "de.dnpm"
ThisBuild / scalaVersion := "2.13.18"
ThisBuild / version      := envOrElse("VERSION","1.0.0")

val ownerRepo  = envOrElse("REPOSITORY","dnpm-dip/central-data-node").split("/")
ThisBuild / githubOwner      := ownerRepo(0)
ThisBuild / githubRepository := ownerRepo(1)


ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", "services", xs @ _*) => MergeStrategy.first
  case PathList("META-INF", xs @ _*)             => MergeStrategy.discard
  case "reference.conf"                          => MergeStrategy.concat
  case _                                         => MergeStrategy.last
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
      dependencies.logback,
      dependencies.service_base,
      dependencies.bfarm_dto_base,
      dependencies.mongo4cats
    ),
    assembly / assemblyJarName := "dnpm-ccdn-core.jar",
    assembly / mainClass       := Some("de.dnpm.ccdn.core.MVHReportingService")
  )

lazy val connectors = project
  .settings(
    name := "ccdn-connectors",
    settings,
    libraryDependencies ++= Seq(
      dependencies.scalatest,
      dependencies.scalamock,
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
    val scalatest      = "org.scalatest"     %% "scalatest"               % "3.2.18" % Test
    val scalamock      = "org.scalamock"     %% "scalamock"               % "7.5.5" % Test
    val logback        = "ch.qos.logback"    %  "logback-classic"         % "1.5.18"
    val play_ahc       = "org.playframework" %% "play-ahc-ws-standalone"  % "3.0.7"
    val play_ahc_js    = "org.playframework" %% "play-ws-standalone-json" % "3.0.7"
    val service_base   = "de.dnpm.dip"       %% "service-base"            % "1.3.1"
    val bfarm_dto_base = "de.dnpm"           %% "dnpm-bfarm-model-base"   % "1.0.1"
    val mongo4cats   = "io.github.kirill5k" %% "mongo4cats-core"         % "0.7.13"
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
)


lazy val commonSettings = Seq(
  scalacOptions ++= compilerOptions,
  resolvers ++= Seq(
    "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
    Resolver.githubPackages("dnpm-dip"),
    Resolver.githubPackages("KohlbacherLab"),
    Resolver.sonatypeCentralSnapshots
  )

)

