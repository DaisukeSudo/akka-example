val scala3Version = "3.2.0"
val akkaVersion = "2.6.20"
val akkaHttpVersion = "10.2.10"

lazy val root = project
  .in(file("."))
  .settings(
    name := "akka-example",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,

    libraryDependencies ++= Seq (
      "org.slf4j" % "slf4j-api" % "2.0.3",
      "ch.qos.logback" % "logback-classic" % "1.4.1",
    ),

    // Akka
    libraryDependencies ++= Seq (
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
      "com.typesafe.akka" %% "akka-persistence-testkit" % akkaVersion % Test,
      ("com.typesafe.akka" %% "akka-http" % akkaHttpVersion).cross(CrossVersion.for3Use2_13),
      ("com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test).cross(CrossVersion.for3Use2_13),
    )
  )
