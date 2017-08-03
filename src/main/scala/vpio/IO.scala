package vpio

import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.index.ZCurveKeyIndexMethod
import geotrellis.spark.io.s3.{S3AttributeStore, S3LayerWriter}
import geotrellis.spark.tiling._
import geotrellis.vectortile.VectorTile
import org.apache.log4j.{Level, Logger}
import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql._
import vectorpipe._
import vectorpipe.util.LayerMetadata
import cats.implicits._
import com.monovore.decline._

// --- //

object IO extends CommandApp(
  name = "vp-orc-io",
  header = "Convert an OSM ORC file into VectorTiles",
  main = {

    /* CLI option handling */
    val orcO = Opts.option[String]("orc", help = "Location of the .orc file to process")
    val bucketO = Opts.option[String]("bucket", help = "S3 bucket to write VTs to")
    val prefixO = Opts.option[String]("key", help = "S3 directory (in bucket) to write to")
    val layerO = Opts.option[String]("layer", help = "Name of the output Layer")
    val localF = Opts.flag("local", help = "Is this to be run locally, not on EMR?").orFalse

    (orcO |@| bucketO |@| prefixO |@| layerO |@| localF).map { (orc, bucket, prefix, layer, local) =>

      println(s"ORC: ${orc}")
      println(s"OUTPUT: ${bucket}/${prefix}")
      println(s"LAYER: ${layer}")

      /* Settings compatible for both local and EMR execution */
      val conf = new SparkConf()
        .setIfMissing("spark.master", "local[*]")
        .setAppName("vp-orc-io")

      implicit val ss: SparkSession = SparkSession.builder
        .config(conf)
        .enableHiveSupport
        .getOrCreate

      /* Silence the damn INFO logger */
      Logger.getRootLogger().setLevel(Level.ERROR)

      /* Necessary for locally reading ORC files off S3 */
      if (local) useS3(ss)

      /* For writing a compressed Tile Layer */
      val writer = S3LayerWriter(S3AttributeStore(bucket, prefix))

      val layout: LayoutDefinition =
        ZoomedLayoutScheme.layoutForZoom(14, WebMercator.worldExtent, 512)

      osm.fromORC(orc) match {
        case Left(e) => println(e)
        case Right((ns,ws,rs)) => {

          /* Assumes that OSM ORC is in LatLng */
          val latlngFeats: RDD[osm.OSMFeature] =
            osm.toFeatures(ns.repartition(100), ws.repartition(10), rs)

          /* Reproject into WebMercator, the default for VTs */
          val wmFeats: RDD[osm.OSMFeature] =
            latlngFeats.repartition(100).map(_.reproject(LatLng, WebMercator))

          /* Associated each Feature with a SpatialKey */
          val fgrid: RDD[(SpatialKey, Iterable[osm.OSMFeature])] =
            VectorPipe.toGrid(Clip.byHybrid, layout, wmFeats)

          /* Create the VectorTiles */
          val tiles: RDD[(SpatialKey, VectorTile)] =
            VectorPipe.toVectorTile(Collate.byAnalytics, layout, fgrid)

          val bounds: KeyBounds[SpatialKey] =
            tiles.map({ case (key, _) => KeyBounds(key, key) }).reduce(_ combine _)

          /* Construct metadata for the Layer */
          val meta = LayerMetadata(layout, bounds)

          writer.write(LayerId(layer, 14), ContextRDD(tiles, meta), ZCurveKeyIndexMethod)
        }
      }

      ss.stop()

      println("Done.")
    }
  }
)
