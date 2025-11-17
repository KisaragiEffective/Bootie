name := "bootie"

version := "0.1.0"

scalaVersion := "3.7.4"

enablePlugins(JavaAppPackaging)

val catsEffectVersion = "3.5.7"
val catsRetryVersion = "3.1.3"
val fs2Version = "3.11.0"
val http4sVersion = "0.23.30"
val circeVersion = "0.14.10"
val scribeVersion = "3.15.2"
val scalikejdbcVersion = "4.3.2"
val flywayVersion = "11.1.0"
val postgresVersion = "42.7.4"

libraryDependencies ++= Seq(
  // cats-effect
  "org.typelevel" %% "cats-effect" % catsEffectVersion,
  "com.github.cb372" %% "cats-retry" % catsRetryVersion,

  // fs2
  "co.fs2" %% "fs2-core" % fs2Version,
  "co.fs2" %% "fs2-io" % fs2Version,

  // http4s
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,

  // circe
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,

  // scribe
  "com.outr" %% "scribe" % scribeVersion,
  "com.outr" %% "scribe-cats" % scribeVersion,

  // scalikejdbc
  "org.scalikejdbc" %% "scalikejdbc" % scalikejdbcVersion,
  "org.scalikejdbc" %% "scalikejdbc-config" % scalikejdbcVersion,

  // flyway
  "org.flywaydb" % "flyway-core" % flywayVersion,
  "org.flywaydb" % "flyway-database-postgresql" % flywayVersion,

  // postgresql
  "org.postgresql" % "postgresql" % postgresVersion,

  // HTML parsing (for scraping)
  "org.jsoup" % "jsoup" % "1.18.3",

  // config
  "com.typesafe" % "config" % "1.4.3"
)

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:higherKinds",
  "-Ykind-projector:underscores"
)
