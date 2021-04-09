name := "zio-slides"

version := "0.1"

val zioVersion    = "0.0.0+1-7f81902b-SNAPSHOT"
val http4sVersion = "0.21.21"

val sharedSettings = Seq(
  addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.11.3" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
  resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  libraryDependencies ++= Seq(
    "dev.zio"      %%% "zio-json"          % "0.1.4",
    "org.scala-lang" % "scala-reflect"     % scalaVersion.value % Provided,
    "dev.zio"      %%% "zio"               % zioVersion,
    "dev.zio"      %%% "zio-test"          % zioVersion         % Test,
    "dev.zio"      %%% "zio-test-magnolia" % zioVersion         % Test,
    "dev.zio"      %%% "zio-macros"        % zioVersion
  ),
  scalaVersion := "2.13.5",
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
)

scalacOptions ++= Seq("-Ymacro-annotations")

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val backend = project
  .in(file("backend"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    sharedSettings,
    libraryDependencies ++= Seq(
      "io.github.kitlangton" %% "zio-magic"           % "0.2.3",
      "dev.zio"              %% "zio-interop-cats"    % "2.4.0.0",
      "org.http4s"           %% "http4s-dsl"          % http4sVersion,
      "org.http4s"           %% "http4s-blaze-server" % http4sVersion,
      "org.http4s"           %% "http4s-circe"        % http4sVersion
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
      "com.raquo"         %%% "laminar"         % "0.12.1",
      "io.github.cquiroz" %%% "scala-java-time" % "2.2.1",
      "io.laminext"       %%% "websocket"       % "0.12.2"
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
