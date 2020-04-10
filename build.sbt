import Dependencies.Ver

name := "mockdevice"
organization := "polyform"
scalaVersion := "2.12.8"
scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-language:postfixOps",
  "-language:implicitConversions",
  "-Ywarn-unused-import",
  "-Xfatal-warnings",
  "-Xlint:_"
)

libraryDependencies := Seq(
  "com.iheart" %% "ficus" % Ver.ficus,
  "com.lihaoyi" %% "requests" % "0.2.0",
  "com.typesafe.akka" %% "akka-http-spray-json" % Ver.akkaHttp,
  "com.lightbend.akka" %% "akka-stream-alpakka-mqtt" % Ver.alpakka,
  "ch.qos.logback" % "logback-classic" % Ver.logback,
  "com.typesafe.scala-logging" %% "scala-logging" % Ver.scalaLogging,
)

enablePlugins(GitVersioning, JavaServerAppPackaging)
dockerExposedPorts := Seq(9000)
dockerUpdateLatest := true

val procVer = "cb3da332c65a11eca0dd00043911f93bb47c6ed0" // 04-09-2020
dependsOn(
  ProjectRef(uri(s"https://gitlab-int.ctc.com/polyform/particle-proxy.git#$procVer"), "proxy"),
)
