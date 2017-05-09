package vpio

import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.raster.DoubleCellType
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.io.index.ZCurveKeyIndexMethod
import geotrellis.spark.tiling._
import geotrellis.vectortile.VectorTile
import geotrellis.vectortile.spark._
import org.apache.log4j.{Level, Logger}
import org.apache.spark._
import org.apache.spark.rdd.RDD
import vectorpipe._

// --- //

object IO extends App {
  override def main(args: Array[String]): Unit = {
    implicit val sc: SparkContext = new SparkContext(
      new SparkConf()
        .setMaster("local[*]")
        .setAppName("vp-simplify")
    )

    /* Silence the damn INFO logger */
    Logger.getRootLogger().setLevel(Level.ERROR)

    /* Path to a OSM XML file */
    val path: String = "data/north-van.osm"

    /* For writing a compressed Tile Layer */
    val catalog: String = "/home/colin/tiles/"
    val store = FileAttributeStore(catalog)
    val writer = FileLayerWriter(store)

    /*
    val catalog: SpatialKey => String = SaveToHadoop.spatialKeyToPath(
      LayerId("north-van", 15),
      "/home/colin/tiles/{name}/{z}/{x}/{y}.mvt"
    )
     */

    val layout: LayoutDefinition =
      ZoomedLayoutScheme.layoutForZoom(15, WebMercator.worldExtent, 512)

    osm.fromLocalXML(path) match {
      case Left(e) => println(e)
      case Right((ns,ws,rs)) => {

        /* Assumes that OSM XML is in LatLng */
        val latlngFeats: RDD[osm.OSMFeature] =
          osm.toFeatures(ns, ws, rs)

        /* Reproject into WebMercator, the default for VTs */
        val wmFeats: RDD[osm.OSMFeature] =
          latlngFeats.map(_.reproject(LatLng, WebMercator))

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

        println(s"TOTAL TILES: ${tiles.count}")

        writer.write(LayerId("north-van", 15), ContextRDD(tiles, meta), ZCurveKeyIndexMethod)

        tiles.take(3).foreach({ case (_,v) => println(v.pretty) })

//        tiles.saveToHadoop(catalog)({ (k,v) => v.toBytes })
      }
    }

    sc.stop()
  }
}
