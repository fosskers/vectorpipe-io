package vpio

import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.raster.DoubleCellType
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.io.index.ZCurveKeyIndexMethod
import geotrellis.spark.tiling._
import geotrellis.vectortile.VectorTile
import org.apache.log4j.{Level, Logger}
import org.apache.spark._
import org.apache.spark.rdd.RDD
import vectorpipe.{Clip, Collate, VectorPipe => VP}
import vectorpipe.osm.{reproject => reproj, _}

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

    VP.fromLocalXML(path) match {
      case Left(e) => println(e)
      case Right((ns,ws,rs)) => {

        /* Assumes that OSM XML is in LatLng */
        val latlngFeats: RDD[OSMFeature] = VP.toFeatures(ns, ws, rs)

        /* Reproject into WebMercator, the default for VTs */
        val wmFeats: RDD[OSMFeature] = latlngFeats.map(reproj(_, LatLng, WebMercator))

        /* Associated each Feature with a SpatialKey */
        val fgrid: RDD[(SpatialKey, Iterable[OSMFeature])] = VP.toGrid(Clip.byHybrid, layout, wmFeats)

        /* Create the VectorTiles */
        val tiles: RDD[(SpatialKey, VectorTile)] = VP.toVectorTile(Collate.byAnalytics, layout, fgrid)

       val minKey = tiles.map(_._1).reduce({ case (acc, k) =>
          SpatialKey(acc.col.min(k.col), acc.row.min(k.row))
        })

        val maxKey = tiles.map(_._1).reduce({ case (acc, k) =>
          SpatialKey(acc.col.max(k.col), acc.row.max(k.row))
        })

        /* Construct metadata for the Layer (while fudging the CellType) */
        val meta = TileLayerMetadata(
          DoubleCellType, layout, layout.extent, WebMercator, KeyBounds(minKey, maxKey)
        )

        println(s"TOTAL TILES: ${tiles.count}")

        writer.write(LayerId("north-van", 15), ContextRDD(tiles, meta), ZCurveKeyIndexMethod)

//        tiles.saveToHadoop(catalog)({ (k,v) => v.toBytes })
      }
    }

    sc.stop()
  }
}
