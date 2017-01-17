name := "whereismyfood"

version := "0.12"

scalaVersion := "2.11.8"
ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }
mainClass in Compile := Some("info.whereismyfood.AppCluster")
//enablePlugins(JavaAppPackaging)
libraryDependencies ++= {
  val akkaVersion = "2.4.16"
  Seq(
    "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.0",
    "com.typesafe.akka" %% "akka-http-core" % "10.0.0",
    "com.typesafe.akka" %% "akka-http" % "10.0.0",
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
    "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
    "com.typesafe" % "config" % "1.3.1",
    
    "com.google.code.gson" % "gson" % "2.7",
    "com.google.maps" % "google-maps-services" % "0.1.16",
    "com.google.cloud" % "google-cloud-datastore" % "0.5.1",
    "com.google.cloud" % "google-cloud-storage" % "0.5.1",
    "com.google.cloud.sql" % "mysql-socket-factory" % "1.0.2",

    "org.slf4j" % "slf4j-api" % "1.7.21",
    "org.slf4j" % "slf4j-simple" % "1.7.21",

    "org.json4s" %% "json4s-native" % "3.5.0",

    "io.igl" % "jwt_2.11" % "1.2.0",
    "ch.megard" % "akka-http-cors_2.11" % "0.1.8",
    "io.spray" %%  "spray-json" % "1.3.2",
    "com.graphhopper" % "jsprit-core" % "1.7-RC1",
    "com.github.etaty" % "rediscala_2.11" % "1.6.0",
    "me.chrons" % "boopickle_2.11" % "1.2.5",
    "com.twilio.sdk" % "twilio-java-sdk" % "7.0.0-rc-10",
    "io.monix" %% "monix" % "2.1.1"
  )
}

assemblyMergeStrategy in assembly := {
  case PathList("reference.conf") => MergeStrategy.concat
  //case PathList("META-INF", xs @ _*) => MergeStrategy.concat
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case x => MergeStrategy.first
}