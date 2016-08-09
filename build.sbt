name := "bookie"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= {
  val akkaStreamVersion = "2.4.9-RC2"
  Seq(
    "com.typesafe.akka" %% "akka-http-experimental" % akkaStreamVersion,
    "com.sksamuel.elastic4s" %% "elastic4s-core" % "2.3.0",
    "org.json4s" %% "json4s-native" % "3.4.0"

  )
}
    