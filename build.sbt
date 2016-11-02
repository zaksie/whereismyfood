name := "whereismyfood"

version := "1.0"

scalaVersion := "2.11.8"
mainClass in Compile := Some("info.whereismyfood.WebServer")

libraryDependencies ++= {
  val akkaStreamVersion = "2.4.11"
  Seq(
    "com.typesafe.akka" %% "akka-http-core" % akkaStreamVersion,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaStreamVersion,
    "com.typesafe.akka" %% "akka-http-jackson-experimental" % akkaStreamVersion,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaStreamVersion,
    "com.typesafe" % "config" % "1.3.1",

    "com.google.maps" % "google-maps-services" % "0.1.16",
    "org.slf4j" % "slf4j-api" % "1.7.21",
    "org.slf4j" % "slf4j-simple" % "1.7.21",
    "com.google.code.gson" % "gson" % "2.7",
    "io.igl" % "jwt_2.11" % "1.2.0",
    "ch.megard" %% "akka-http-cors" % "0.1.8",
    "io.spray" %%  "spray-json" % "1.3.2",
    "com.graphhopper" % "jsprit-core" % "1.7-RC1",
    "com.github.etaty" %% "rediscala" % "1.6.0",
    "me.chrons" %% "boopickle" % "1.2.4",
    "com.google.cloud" % "google-cloud-datastore" % "0.5.0",
    "com.twilio.sdk" % "twilio-java-sdk" % "7.0.0-rc-10"
  )
}