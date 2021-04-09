name := "zio-slides"

version := "0.1"

val zioVersion = "1.0.5+90-9b816198-SNAPSHOT"

val sharedSettings = Seq(
  addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.11.3" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
  resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  libraryDependencies ++= Seq(
    "dev.zio"      %%% "zio"           % zioVersion,
    "dev.zio"      %%% "zio-streams"   % zioVersion,
    "dev.zio"      %%% "zio-json"      % "0.1.4",
    "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
  ),
  scalaVersion := "2.13.5"
)

scalacOptions ++= Seq("-Ymacro-annotations", "-Xfatal-warnings")

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val backend = project
  .in(file("backend"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    sharedSettings,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      "io.github.kitlangton" %% "zio-magic" % "0.2.3",
      "dev.zio"              %% "zio-test"  % zioVersion % Test,
      "io.d11"               %% "zhttp"     % "1.0.0.0-RC15"
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
