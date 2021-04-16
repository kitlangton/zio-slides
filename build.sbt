name := "zio-slides"

version := "0.1"

val animusVersion    = "0.1.5+6-ceaea93a+20210415-1645-SNAPSHOT"
val laminarVersion   = "0.12.2"
val zioConfigVersion = "1.0.4"
val zioHttpVersion   = "1.0.0.0-RC15+7-54a6202a-SNAPSHOT"
val zioVersion       = "1.0.5+99-0699c11e-SNAPSHOT"

val sharedSettings = Seq(
  addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.11.3" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
  scalacOptions ++= Seq("-Ymacro-annotations", "-Xfatal-warnings"),
  resolvers ++= Seq(
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    "Sonatype OSS Snapshots s01" at "https://s01.oss.sonatype.org/content/repositories/snapshots"
  ),
  libraryDependencies ++= Seq(
    "dev.zio"      %%% "zio"           % zioVersion,
    "dev.zio"      %%% "zio-streams"   % zioVersion,
    "dev.zio"      %%% "zio-json"      % "0.1.4",
    "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
  ),
  scalaVersion := "2.13.5"
)

scalacOptions ++= Seq("-Ymacro-annotations", "-Xfatal-warnings")

//resolvers ++= Seq(
//  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
//  "Sonatype OSS Snapshots s01" at "https://s01.oss.sonatype.org/content/repositories/snapshots"
//)

lazy val backend = project
  .in(file("backend"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    sharedSettings,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      "io.github.kitlangton" %% "zio-magic"           % "0.2.3",
      "dev.zio"              %% "zio-config"          % zioConfigVersion,
      "dev.zio"              %% "zio-config-magnolia" % zioConfigVersion,
      "dev.zio"              %% "zio-test"            % zioVersion % Test,
      "io.d11"               %% "zhttp"               % zioHttpVersion
    )
  )
  .dependsOn(shared)

lazy val frontend = project
  .in(file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
    scalaJSLinkerConfig ~= { _.withSourceMap(false) },
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "io.github.kitlangton" %%% "animus"          % animusVersion,
      "com.raquo"            %%% "laminar"         % laminarVersion,
      "io.github.cquiroz"    %%% "scala-java-time" % "2.2.1",
      "io.laminext"          %%% "websocket"       % "0.12.2"
    )
  )
  .settings(sharedSettings)
  .dependsOn(shared)

lazy val shared = project
  .enablePlugins(ScalaJSPlugin)
  .in(file("shared"))
  .settings(
    sharedSettings,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
    scalaJSLinkerConfig ~= { _.withSourceMap(false) }
  )
