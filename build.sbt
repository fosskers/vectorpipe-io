name := """vp-io-test"""

version := "1.0.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.apache.spark"            %% "spark-core"            % "2.1.0",
  "org.locationtech.geotrellis" %% "geotrellis-spark"      % "1.1.0-SNAPSHOT",
  "org.locationtech.geotrellis" %% "geotrellis-s3"         % "1.1.0-SNAPSHOT",
  "org.locationtech.geotrellis" %% "geotrellis-vector"     % "1.1.0-SNAPSHOT",
  "org.locationtech.geotrellis" %% "geotrellis-vectortile" % "1.1.0-SNAPSHOT",
  "com.azavea"                  %% "vectorpipe"            % "1.0.0"
)
