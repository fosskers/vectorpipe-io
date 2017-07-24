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

// --- //

object IO extends App {
  override def main(args: Array[String]): Unit = {

    implicit val ss: SparkSession = SparkSession.builder
      .master("local[*]")
      .appName("vp-orc-io")
      .enableHiveSupport
      .getOrCreate

    /* Silence the damn INFO logger */
    Logger.getRootLogger().setLevel(Level.ERROR)

    /* Necessary for reading ORC files off S3 */
    useS3(ss)

    val source: String = "vancouver"

    /* Path to an OSM ORC file */
    val path: String = s"s3://vectortiles/orc/${source}.orc"

    /* For writing a compressed Tile Layer */
    val store = S3AttributeStore("vectortiles", "orc-catalog")
    val writer = S3LayerWriter(store)

    val layout: LayoutDefinition =
      ZoomedLayoutScheme.layoutForZoom(14, WebMercator.worldExtent, 512)

    osm.fromORC(path) match {
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

        writer.write(LayerId(source, 14), ContextRDD(tiles, meta), ZCurveKeyIndexMethod)
      }
    }

    ss.stop()
  }
}
