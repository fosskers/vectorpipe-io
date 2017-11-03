name := """vp-io-test"""

version := "1.0.0"

scalaVersion := "2.11.11"

/* To massage `cats` a little bit */
scalacOptions := Seq(
  "-Ypartial-unification"
)

/* For `decline` dependency */
resolvers += Resolver.bintrayRepo("bkirwi", "maven")

libraryDependencies ++= Seq(
  "com.azavea"                  %% "vectorpipe"                  % "1.0.0-SNAPSHOT",
  "com.monovore"                %% "decline"                     % "0.4.0-M1",
  "org.apache.spark"            %% "spark-hive"                  % "2.2.0" % "provided",
  "org.locationtech.geotrellis" %% "geotrellis-s3"               % "1.2.0-SNAPSHOT",
  "org.locationtech.geotrellis" %% "geotrellis-spark"            % "1.2.0-SNAPSHOT",
  "org.locationtech.geotrellis" %% "geotrellis-vector"           % "1.2.0-SNAPSHOT",
  "org.locationtech.geotrellis" %% "geotrellis-vectortile"       % "1.2.0-SNAPSHOT",
  "org.typelevel"               %% "cats-core"                   % "1.0.0-MF"
)

/* Allow `run` to be used with Spark code, while assembling fat JARs w/o Spark bundled */
// run in Compile := Defaults.runTask(fullClasspath in Compile, mainClass in (Compile, run), runner in (Compile, run)).evaluated
// runMain in Compile := Defaults.runMainTask(fullClasspath in Compile, runner in(Compile, run)).evaluated

assemblyMergeStrategy in assembly := {
  case "reference.conf" => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case "META-INF/MANIFEST.MF" => MergeStrategy.discard
  case "META-INF\\MANIFEST.MF" => MergeStrategy.discard
  case "META-INF/ECLIPSEF.RSA" => MergeStrategy.discard
  case "META-INF/ECLIPSEF.SF" => MergeStrategy.discard
  case _ => MergeStrategy.first
}

// run --orc s3://vectortiles/orc/europe/andorra.orc --bucket vectortiles --key orc-catalog --layer andorra --local
// run --orc /home/colin/code/playground/scala/orc/ireland.orc --bucket vectortiles --key orc-catalog --layer ireland --local
// run --orc /home/colin/code/azavea/vp-io-test/georgia.orc --bucket vectortiles --key orc-catalog --layer georgia --local
