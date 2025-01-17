/*
 * Copyright 2009-2017. DigitalGlobe, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.mrgeo.utils

import java.io.{File, FileInputStream, IOException, InputStreamReader}
import java.net.URL
import java.util.Properties

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.mapreduce.Job
import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.mrgeo.data.image.{ImageInputFormatContext, ImageOutputFormatContext, MrsImageDataProvider}
import org.mrgeo.data.raster.RasterWritable
import org.mrgeo.data.rdd.{AutoPersister, RasterRDD}
import org.mrgeo.data.tile._
import org.mrgeo.data.{DataProviderFactory, MrsPyramidInputFormat, ProviderProperties}
import org.mrgeo.hdfs.tile.FileSplit.FileSplitInfo
import org.mrgeo.image.{ImageStats, MrsPyramid, MrsPyramidMetadata}
import org.mrgeo.utils.tms.{Bounds, Pixel, TMSUtils}

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scala.collection.{Map, mutable}

@SuppressFBWarnings(value = Array("NP_LOAD_OF_KNOWN_NULL_VALUE"), justification = "Scala generated code")
object SparkUtils extends Logging {

  @deprecated("Use RasterRDD method instead", "")
  def calculateSplitData(rdd:RDD[(TileIdWritable, RasterWritable)]):Array[FileSplitInfo] = {
    calculateSplitData(RasterRDD(rdd))
  }

  def calculateSplitData(rdd:RasterRDD):Array[FileSplitInfo] = {
    // calculate the min/max tile id for each partition
    val partitions = rdd.mapPartitionsWithIndex((partition, data) => {
      var startId = Long.MaxValue
      var endId = Long.MinValue

      // not sure if the name is always part-r-xxxxx, but we'll use it for now.
      val name = f"part-r-$partition%05d"

      data.foreach(tile => {
        startId = Math.min(startId, tile._1.get())
        endId = Math.max(endId, tile._1.get())
      })

      val split = new FileSplitInfo(startId, endId, name, partition)

      val result = ListBuffer[(Int, FileSplitInfo)]()

      result.append((partition, split))

      result.iterator
    }, preservesPartitioning = true)


    val splits = Array.ofDim[FileSplitInfo](rdd.partitions.length)

    // collect the results and set the up the array
    partitions.collect().foreach(part => {
      splits(part._1) = part._2
    })

    splits
  }


  def getConfiguration:SparkConf = {

    val conf = new SparkConf()
    loadDefaultSparkProperties(conf)

    conf
  }

  @deprecated("Use RasterRDD method instead", "")
  def loadMrsPyramidAndMetadataRDD(imageName:String, context:SparkContext):
  (RDD[(TileIdWritable, RasterWritable)], MrsPyramidMetadata) = {

    val providerProps:ProviderProperties = null
    val dp:MrsImageDataProvider = DataProviderFactory.getMrsImageDataProvider(imageName,
      DataProviderFactory.AccessMode.READ, providerProps)
    val metadata:MrsPyramidMetadata = dp.getMetadataReader.read()

    (loadMrsPyramidRDD(dp, metadata.getMaxZoomLevel, context), metadata)
  }

  @deprecated("Use RasterRDD method instead", "")
  def loadMrsPyramidAndMetadata(imageName:String, zoom:Int, bounds:Bounds, context:SparkContext):
  (RDD[(TileIdWritable, RasterWritable)], MrsPyramidMetadata) = {

    val providerProps:ProviderProperties = null
    val dp:MrsImageDataProvider = DataProviderFactory.getMrsImageDataProvider(imageName,
      DataProviderFactory.AccessMode.READ, providerProps)
    val metadata:MrsPyramidMetadata = dp.getMetadataReader.read()

    (loadMrsPyramidRDD(dp, zoom, bounds, context), metadata)
  }

  @deprecated("Use RasterRDD method instead", "")
  def loadMrsPyramidRDD(provider:MrsImageDataProvider, zoom:Int, bounds:Bounds,
                        context:SparkContext):RDD[(TileIdWritable, RasterWritable)] = {
    val metadata:MrsPyramidMetadata = provider.getMetadataReader.read()

    val conf1 = provider.setupSparkJob(context.hadoopConfiguration)
    val tifc = new ImageInputFormatContext(zoom, metadata.getTilesize,
      provider.getResourceName, bounds, provider.getProviderProperties)
    val ifp = provider.getImageInputFormatProvider(tifc)
    val conf2 = ifp.setupSparkJob(conf1, provider)

    //    MrsImageDataProvider.setupMrsPyramidSingleSimpleInputFormat(job, provider.getResourceName,
    //      zoom, metadata.getTilesize, null, providerProps) // null for bounds means use all tiles (no cropping)

    // build a phony job...
    val job = Job.getInstance(conf2)
    //    val inputFormatClass: Class[InputFormat[TileIdWritable, RasterWritable]] = job.getInputFormatClass
    //        .asInstanceOf[Class[InputFormat[TileIdWritable, RasterWritable]]]

    //    log.warn("Running loadPyramid with configuration " + job.getConfiguration + " with input format " +
    //      inputFormatClass.getName)
    val rdd = context.newAPIHadoopRDD(job.getConfiguration,
      classOf[MrsPyramidInputFormat],
      classOf[TileIdWritable],
      classOf[RasterWritable])

    rdd.name = provider.getResourceName
    rdd

    //        FileInputFormat.addInputPath(job, new Path(provider.getResourceName, zoom.toString))
    //        FileInputFormat.setInputPathFilter(job, classOf[MapFileFilter])
    //
    //        context.newAPIHadoopRDD(job.getConfiguration,
    //          classOf[SequenceFileInputFormat[TileIdWritable, RasterWritable]],
    //          classOf[TileIdWritable],
    //          classOf[RasterWritable])
  }

  @deprecated("Use RasterRDD method instead", "")
  def loadMrsPyramidRDD(imageName:String, context:SparkContext):RDD[(TileIdWritable, RasterWritable)] = {
    val providerProps:ProviderProperties = null
    val dp:MrsImageDataProvider = DataProviderFactory.getMrsImageDataProvider(imageName,
      DataProviderFactory.AccessMode.READ, providerProps)

    val metadata:MrsPyramidMetadata = dp.getMetadataReader.read()

    loadMrsPyramidRDD(dp, metadata.getMaxZoomLevel, context)
  }

  @deprecated("Use RasterRDD method instead", "")
  def loadMrsPyramidRDD(provider:MrsImageDataProvider, zoom:Int,
                        context:SparkContext):RDD[(TileIdWritable, RasterWritable)] = {
    val metadata:MrsPyramidMetadata = provider.getMetadataReader.read()

    val conf1 = provider.setupSparkJob(context.hadoopConfiguration)
    val tifc = new ImageInputFormatContext(zoom, metadata.getTilesize,
      provider.getResourceName, provider.getProviderProperties)
    val ifp = provider.getImageInputFormatProvider(tifc)
    val conf2 = ifp.setupSparkJob(conf1, provider)

    //    MrsImageDataProvider.setupMrsPyramidSingleSimpleInputFormat(job, provider.getResourceName,
    //      zoom, metadata.getTilesize, null, providerProps) // null for bounds means use all tiles (no cropping)

    // build a phony job...
    val job = Job.getInstance(conf2)
    //    val inputFormatClass: Class[InputFormat[TileIdWritable, RasterWritable]] = job.getInputFormatClass
    //        .asInstanceOf[Class[InputFormat[TileIdWritable, RasterWritable]]]

    //    log.warn("Running loadPyramid with configuration " + job.getConfiguration + " with input format " +
    //      inputFormatClass.getName)
    val rdd = context.newAPIHadoopRDD(job.getConfiguration,
      classOf[MrsPyramidInputFormat],
      classOf[TileIdWritable],
      classOf[RasterWritable])

    rdd.name = provider.getResourceName
    rdd

    //        FileInputFormat.addInputPath(job, new Path(provider.getResourceName, zoom.toString))
    //        FileInputFormat.setInputPathFilter(job, classOf[MapFileFilter])
    //
    //        context.newAPIHadoopRDD(job.getConfiguration,
    //          classOf[SequenceFileInputFormat[TileIdWritable, RasterWritable]],
    //          classOf[TileIdWritable],
    //          classOf[RasterWritable])
  }

  @deprecated("Use RasterRDD method instead", "")
  def loadMrsPyramidRDD(imageName:String, zoom:Int, context:SparkContext):RDD[(TileIdWritable, RasterWritable)] = {
    val providerProps:ProviderProperties = null
    val dp:MrsImageDataProvider = DataProviderFactory.getMrsImageDataProvider(imageName,
      DataProviderFactory.AccessMode.READ, providerProps)

    loadMrsPyramidRDD(dp, zoom, context)
  }

  @deprecated("Use RasterRDD method instead", "")
  def loadMrsPyramidRDD(provider:MrsImageDataProvider,
                        context:SparkContext):RDD[(TileIdWritable, RasterWritable)] = {
    val metadata:MrsPyramidMetadata = provider.getMetadataReader.read()

    loadMrsPyramidRDD(provider, metadata.getMaxZoomLevel, context)
  }

  def loadMrsPyramidAndMetadata(imageName:String, context:SparkContext):(RasterRDD, MrsPyramidMetadata) = {

    val providerProps:ProviderProperties = null
    val dp:MrsImageDataProvider = DataProviderFactory.getMrsImageDataProvider(imageName,
      DataProviderFactory.AccessMode.READ, providerProps)
    val metadata:MrsPyramidMetadata = dp.getMetadataReader.read()

    (loadMrsPyramid(dp, metadata.getMaxZoomLevel, context), metadata)
  }

  def loadMrsPyramidAndMetadata(provider:MrsImageDataProvider,
                                context:SparkContext):(RasterRDD, MrsPyramidMetadata) = {
    val metadata:MrsPyramidMetadata = provider.getMetadataReader.read()
    (loadMrsPyramid(provider, metadata.getMaxZoomLevel, context), metadata)
  }

  def loadMrsPyramidAndMetadata(provider:MrsImageDataProvider, bounds:Bounds,
                                context:SparkContext):(RasterRDD, MrsPyramidMetadata) = {
    val metadata:MrsPyramidMetadata = provider.getMetadataReader.read()
    (loadMrsPyramid(provider, metadata.getMaxZoomLevel, bounds, context), metadata)
  }

  def loadMrsPyramidAndMetadata(provider:MrsImageDataProvider, zoom:Int,
                                context:SparkContext):(RasterRDD, MrsPyramidMetadata) = {
    val metadata:MrsPyramidMetadata = provider.getMetadataReader.read()
    (loadMrsPyramid(provider, zoom, context), metadata)
  }

  def loadMrsPyramid(provider:MrsImageDataProvider, zoom:Int, context:SparkContext):RasterRDD = {
    val metadata:MrsPyramidMetadata = provider.getMetadataReader.read()

    val conf1 = provider.setupSparkJob(context.hadoopConfiguration)
    val tifc = new ImageInputFormatContext(zoom, metadata.getTilesize,
      provider.getResourceName, provider.getProviderProperties)
    val ifp = provider.getImageInputFormatProvider(tifc)
    val conf2 = ifp.setupSparkJob(conf1, provider)

    //    MrsImageDataProvider.setupMrsPyramidSingleSimpleInputFormat(job, provider.getResourceName,
    //      zoom, metadata.getTilesize, null, providerProps) // null for bounds means use all tiles (no cropping)

    // build a phony job...
    val job = Job.getInstance(conf2)
    //    val inputFormatClass: Class[InputFormat[TileIdWritable, RasterWritable]] = job.getInputFormatClass
    //        .asInstanceOf[Class[InputFormat[TileIdWritable, RasterWritable]]]

    //    log.warn("Running loadPyramid with configuration " + job.getConfiguration + " with input format " +
    //      inputFormatClass.getName)

    logInfo("Loading MrsPyramid " + provider.getResourceName)

    RasterRDD(context.newAPIHadoopRDD(job.getConfiguration,
      classOf[MrsPyramidInputFormat],
      classOf[TileIdWritable],
      classOf[RasterWritable]))

    //        FileInputFormat.addInputPath(job, new Path(provider.getResourceName, zoom.toString))
    //        FileInputFormat.setInputPathFilter(job, classOf[MapFileFilter])
    //
    //        context.newAPIHadoopRDD(job.getConfiguration,
    //          classOf[SequenceFileInputFormat[TileIdWritable, RasterWritable]],
    //          classOf[TileIdWritable],
    //          classOf[RasterWritable])
  }

  def loadMrsPyramidAndMetadata(provider:MrsImageDataProvider, zoom:Int, bounds:Bounds,
                                context:SparkContext):(RasterRDD, MrsPyramidMetadata) = {
    val metadata:MrsPyramidMetadata = provider.getMetadataReader.read()
    (loadMrsPyramid(provider, zoom, bounds, context), metadata)
  }

  def loadMrsPyramid(provider:MrsImageDataProvider, zoom:Int, bounds:Bounds, context:SparkContext):RasterRDD = {
    val metadata:MrsPyramidMetadata = provider.getMetadataReader.read()

    val conf1 = provider.setupSparkJob(context.hadoopConfiguration)
    val tifc = new ImageInputFormatContext(zoom, metadata.getTilesize,
      provider.getResourceName, bounds, provider.getProviderProperties)
    val ifp = provider.getImageInputFormatProvider(tifc)
    val conf2 = ifp.setupSparkJob(conf1, provider)

    //    MrsImageDataProvider.setupMrsPyramidSingleSimpleInputFormat(job, provider.getResourceName,
    //      zoom, metadata.getTilesize, null, providerProps) // null for bounds means use all tiles (no cropping)

    // build a phony job...
    val job = Job.getInstance(conf2)
    //    val inputFormatClass: Class[InputFormat[TileIdWritable, RasterWritable]] = job.getInputFormatClass
    //        .asInstanceOf[Class[InputFormat[TileIdWritable, RasterWritable]]]

    //    log.warn("Running loadPyramid with configuration " + job.getConfiguration + " with input format " +
    //      inputFormatClass.getName)
    RasterRDD(context.newAPIHadoopRDD(job.getConfiguration,
      classOf[MrsPyramidInputFormat],
      classOf[TileIdWritable],
      classOf[RasterWritable]))

    //        FileInputFormat.addInputPath(job, new Path(provider.getResourceName, zoom.toString))
    //        FileInputFormat.setInputPathFilter(job, classOf[MapFileFilter])
    //
    //        context.newAPIHadoopRDD(job.getConfiguration,
    //          classOf[SequenceFileInputFormat[TileIdWritable, RasterWritable]],
    //          classOf[TileIdWritable],
    //          classOf[RasterWritable])
  }

  def saveMrsPyramidMetadata(imageName:String, context:SparkContext, metadata:MrsPyramidMetadata,
                             providerProps:ProviderProperties): Unit = {
    val dp:MrsImageDataProvider = DataProviderFactory.getMrsImageDataProvider(imageName,
      DataProviderFactory.AccessMode.READ, providerProps)
    val mdWriter= dp.getMetadataWriter
    mdWriter.write(metadata)
  }

  def loadMrsPyramid(imageName:String, context:SparkContext):RasterRDD = {
    val providerProps:ProviderProperties = null
    val dp:MrsImageDataProvider = DataProviderFactory.getMrsImageDataProvider(imageName,
      DataProviderFactory.AccessMode.READ, providerProps)

    val metadata:MrsPyramidMetadata = dp.getMetadataReader.read()

    loadMrsPyramid(dp, metadata.getMaxZoomLevel, context)
  }

  def loadMrsPyramid(imageName:String, zoom:Int, context:SparkContext):RasterRDD = {
    val providerProps:ProviderProperties = null
    val dp:MrsImageDataProvider = DataProviderFactory.getMrsImageDataProvider(imageName,
      DataProviderFactory.AccessMode.READ, providerProps)

    loadMrsPyramid(dp, zoom, context)
  }

  def loadMrsPyramid(provider:MrsImageDataProvider, context:SparkContext):RasterRDD = {
    val metadata:MrsPyramidMetadata = provider.getMetadataReader.read()

    loadMrsPyramid(provider, metadata.getMaxZoomLevel, context)
  }

  @deprecated("Use RasterRDD method instead", "")
  def saveMrsPyramidRDD(tiles:RDD[(TileIdWritable, RasterWritable)], inputProvider:MrsImageDataProvider,
                        zoom:Int, conf:Configuration, providerproperties:ProviderProperties):Unit = {
    saveMrsPyramid(RasterRDD(tiles), inputProvider, zoom, conf, providerproperties)
  }

  def saveMrsPyramid(tiles:RasterRDD, inputProvider:MrsImageDataProvider,
                     zoom:Int, conf:Configuration, providerproperties:ProviderProperties):Unit = {

    val metadata = inputProvider.getMetadataReader.read()

    //    val bounds = metadata.getBounds
    //    val bands = metadata.getBands
    //    val tiletype = metadata.getTileType
    //    val tilesize = metadata.getTilesize
    //    val nodatas = metadata.getDefaultValues
    //    val protectionlevel = metadata.getProtectionLevel

    // NOTE:  This is a very special case where we are adding levels to a pyramid (i.e. BuildPyramid).
    // The input data provider provides most of the parameters.
    //    saveMrsPyramid(tiles, inputProvider, zoom, tilesize, nodatas, conf,
    //      tiletype, bounds, bands, protectionlevel, providerproperties)
    saveMrsPyramid(tiles, inputProvider, metadata, zoom, conf, providerproperties)
  }

  def saveMrsPyramid(tiles:RasterRDD, outputProvider:MrsImageDataProvider,
                     zoom:Int, tilesize:Int, nodatas:Array[Double], conf:Configuration, tiletype:Int = -1,
                     bounds:Bounds = null, bands:Int = -1,
                     protectionlevel:String = null,
                     providerproperties:ProviderProperties = new ProviderProperties()):Unit = {

    val metadata = new MrsPyramidMetadata
    metadata.setMaxZoomLevel(zoom)
    metadata.setTilesize(tilesize)
    metadata.setDefaultValues(nodatas)
    metadata.setTileType(tiletype)
    metadata.setBounds(bounds)
    metadata.setBands(bands)
    metadata.setProtectionLevel(protectionlevel)

    saveMrsPyramid(tiles, outputProvider, metadata, zoom, conf, providerproperties)
  }

  def saveMrsPyramid(tiles:RasterRDD, outputProvider:MrsImageDataProvider, metadata:MrsPyramidMetadata,
                     zoom:Int, conf:Configuration, providerproperties:ProviderProperties):Unit = {

    AutoPersister.incrementRef(tiles)

    //    val localpersist = if (tiles.getStorageLevel == StorageLevel.NONE) {
    //      tiles.persist(StorageLevel.MEMORY_AND_DISK_SER)
    //      true
    //    }
    //    else {
    //      false
    //    }

    val output = outputProvider.getResourceName

    val tilesize = metadata.getTilesize

    if (metadata.getBounds == null) {
      metadata.setBounds(SparkUtils.calculateBounds(tiles, zoom, tilesize))
    }
    val bounds = metadata.getBounds

    val tile = RasterWritable.toMrGeoRaster(tiles.first()._2)
    if (metadata.getBands <= 0 || metadata.getTileType <= 0) {

      metadata.setBands(tile.bands())
      metadata.setTileType(tile.datatype())
    }

    metadata.setName(zoom, zoom.toString)


    val bands = metadata.getBands

    val stats = SparkUtils.calculateStats(tiles, bands, metadata.getDefaultValues)

    val tofc = new ImageOutputFormatContext(output, bounds, zoom, tilesize,
      metadata.getProtectionLevel, metadata.getTileType, bands)
    val tofp = outputProvider.getTiledOutputFormatProvider(tofc)

    tofp.save(tiles, conf)

    // calculate and save metadata
    MrsPyramid.calculateMetadata(zoom, tile, outputProvider, stats, metadata)

    AutoPersister.decrementRef(tiles)
  }

  @deprecated("Use RasterRDD method instead", "")
  def calculateStats(rdd:RDD[(TileIdWritable, RasterWritable)], bands:Int,
                     nodata:Array[Double]):Array[ImageStats] = {

    calculateStats(RasterRDD(rdd), bands, nodata)
  }

  def calculateStats(rdd:RasterRDD, bands:Int,
                     nodata:Array[Double]):Array[ImageStats] = {

    val zero = Array.ofDim[ImageStats](bands)

    for (i <- zero.indices) {
      zero(i) = new ImageStats(Double.MaxValue, Double.MinValue, 0, 0)
    }

    val stats = rdd.aggregate(zero)((stats, t) => {
      val tile = RasterWritable.toMrGeoRaster(t._2); // RasterWritable.toRaster(t._2)

      var y:Int = 0
      while (y < tile.height()) {
        var x:Int = 0
        while (x < tile.width()) {
          var b:Int = 0
          while (b < tile.bands()) {
            val p = tile.getPixelDouble(x, y, b)
            if (FloatUtils.isNotNodata(p, nodata(b).doubleValue())) {
              stats(b).count += 1
              stats(b).sum += p
              stats(b).max = Math.max(stats(b).max, p)
              stats(b).min = Math.min(stats(b).min, p)
            }
            b += 1
          }
          x += 1
        }
        y += 1
      }

      stats
    },
      (stat1, stat2) => {
        val aggstat = stat1.clone()

        for (b <- aggstat.indices) {
          aggstat(b).count += stat2(b).count
          aggstat(b).sum += stat2(b).sum
          aggstat(b).max = Math.max(aggstat(b).max, stat2(b).max)
          aggstat(b).min = Math.min(aggstat(b).min, stat2(b).min)
        }

        aggstat
      })

    for (i <- stats.indices) {
      if (stats(i).count > 0) {
        stats(i).mean = stats(i).sum / stats(i).count
      }
    }

    stats
  }

  def calculateBounds(rdd:RasterRDD, zoom:Int, tilesize:Int):Bounds = {

    val bounds = rdd.aggregate(null.asInstanceOf[Bounds])((bounds:Bounds, t) => {
      val tile = TMSUtils.tileid(t._1.get, zoom)
      val tb = TMSUtils.tileBounds(tile.tx, tile.ty, zoom, tilesize)

      if (bounds == null) {
        tb
      }
      else {
        tb.expand(bounds)
      }
    },
      (tb1, tb2) => {
        if (tb1 == null) {
          tb2
        }
        else {
          tb1.expand(tb2)
        }
      })

    bounds
  }

  @SuppressFBWarnings(value = Array("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"),
    justification = "Scala generated code")
  @SuppressFBWarnings(value = Array("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE"), justification = "Scala generated code")
  def mergeTiles(rdd:RasterRDD, zoom:Int, tilesize:Int, nodatas:Array[Double], bounds:Bounds = null) = {

    val bnds = if (bounds != null) {
      bounds
    }
    else {
      SparkUtils.calculateBounds(RasterRDD(rdd), zoom, tilesize)
    }

    val tilebounds = TMSUtils.tileBounds(bnds, zoom, tilesize)

    val ul = TMSUtils.latLonToPixelsUL(tilebounds.n, tilebounds.w, zoom, tilesize)
    val lr = TMSUtils.latLonToPixelsUL(tilebounds.s, tilebounds.e, zoom, tilesize)

    val width = (lr.px - ul.px).toInt
    val height = (lr.py - ul.py).toInt

    log.debug("w: {} h: {}", width, height)

    val sample = RasterWritable.toMrGeoRaster(rdd.first()._2)
    val merged = sample.createCompatibleEmptyRaster(width, height, nodatas)

    // because the data is distributed. and could be large, we need to collect a single partition at a time...
    rdd.partitions.foreach(partition => {
      val idx = partition.index
      val partrdd = rdd.mapPartitionsWithIndex((part, data) => {
        if (part == idx) {
          data
        }
        else {
          Iterator()
        }
      }, preservesPartitioning = true)

      val collected = partrdd.collect()
      collected.foreach(tile => {
        val id = TMSUtils.tileid(tile._1.get, zoom)
        val tb = TMSUtils.tileBounds(id.tx, id.ty, zoom, tilesize)

        // calculate the starting pixel for the source
        // make sure we use the upper-left lat/lon
        val start = TMSUtils.latLonToPixelsUL(tb.n, tb.w, zoom, tilesize)

        val source = RasterWritable.toMrGeoRaster(tile._2)
        logDebug(s"Tile ${id.tx}, ${id.ty} with bounds ${tb.w}, ${tb.s}, ${tb.e}, ${tb.n}" +
                 s" pasted onto px ${start.px - ul.px} py ${start.py - ul.py}")

        merged
            .copyFrom(0, 0, source.width(), source.height(), source, (start.px - ul.px).toInt, (start.py - ul.py).toInt)
      })
    })

    val finalul = TMSUtils.latLonToPixelsUL(bnds.n, bnds.w, zoom, tilesize)
    val finallr = TMSUtils.latLonToPixelsUL(bnds.s, bnds.e, zoom, tilesize)

    val finalwidth = (finallr.px - finalul.px).toInt
    val finalheight = (finallr.py - finalul.py).toInt

    // if we need to, crop the image
    if (finalul != ul || finallr != lr || finalwidth != width || finalheight != height) {
      merged.clip((finalul.px - ul.px).toInt, (finalul.py - ul.py).toInt,
        finalwidth, finalheight)
    }
    else {
      merged
    }
  }

  def calculateBoundsAndStats(rdd:RasterRDD, bands:Int, zoom:Int, tilesize:Int,
                              nodata:Array[Double]):(Bounds, Array[ImageStats]) = {
    val zero = Array.ofDim[ImageStats](bands)

    for (i <- zero.indices) {
      zero(i) = new ImageStats(Double.MaxValue, Double.MinValue, 0, 0)
    }

    val result = rdd.aggregate((null.asInstanceOf[Bounds], zero))((entry, t) => {

      def isNotNodata(value:Double, nodata:Double):Boolean = {
        if (nodata.isNaN) {
          !value.isNaN
        }
        else {
          nodata != value
        }
      }

      val bounds = entry._1
      val stats = entry._2
      val tile = TMSUtils.tileid(t._1.get, zoom)

      // Handle the bounds
      val tb = if (entry._1 == null) {
        TMSUtils.tileBounds(tile.tx, tile.ty, zoom, tilesize)
      }
      else {
        TMSUtils.tileBounds(tile.tx, tile.ty, zoom, tilesize).expand(entry._1)
      }

      // Handle the stats
      val raster = RasterWritable.toMrGeoRaster(t._2)

      var b = 0
      while (b < raster.bands()) {
        var y = 0
        val nd = nodata(b).doubleValue()
        while (y < raster.height()) {
          var x = 0
          while (x < raster.width()) {
            val p = raster.getPixelDouble(x, y, b)
            if (isNotNodata(p, nd)) {
              stats(b).count += 1
              stats(b).sum += p
              stats(b).max = Math.max(stats(b).max, p)
              stats(b).min = Math.min(stats(b).min, p)
            }
            x += 1
          }
          y += 1
        }
        b += 1
      }

      (tb, stats)
    },
      (result1, result2) => {
        // combine the bounds
        val bounds = if (result1._1 == null) {
          result2._1
        }
        else {
          result1._1.expand(result2._1)
        }
        // combine the stats
        val aggstat = result1._2.clone()

        for (b <- aggstat.indices) {
          aggstat(b).count += result2._2(b).count
          aggstat(b).sum += result2._2(b).sum
          aggstat(b).max = Math.max(aggstat(b).max, result2._2(b).max)
          aggstat(b).min = Math.min(aggstat(b).min, result2._2(b).min)
        }

        (bounds, aggstat)
      })
    for (i <- result._2.indices) {
      if (result._2(i).count > 0) {
        result._2(i).mean = result._2(i).sum / result._2(i).count
      }
    }

    result
  }

  @deprecated("Use RasterRDD method instead", "")
  def saveMrsPyramidRDD(tiles:RDD[(TileIdWritable, RasterWritable)],
                        outputProvider:MrsImageDataProvider, inputprovider:MrsImageDataProvider,
                        zoom:Int, conf:Configuration, providerproperties:ProviderProperties):Unit = {
    saveMrsPyramid(RasterRDD(tiles), outputProvider, inputprovider, zoom, conf, providerproperties)
  }

  def saveMrsPyramid(tiles:RasterRDD, outputProvider:MrsImageDataProvider, inputprovider:MrsImageDataProvider,
                     zoom:Int, conf:Configuration, providerproperties:ProviderProperties):Unit = {

    val metadata = inputprovider.getMetadataReader.read()

    val bounds = metadata.getBounds
    val bands = metadata.getBands
    val tiletype = metadata.getTileType
    val tilesize = metadata.getTilesize
    val nodatas = metadata.getDefaultValues
    val protectionlevel = metadata.getProtectionLevel

    saveMrsPyramid(tiles, outputProvider, zoom, tilesize, nodatas, conf,
      tiletype, bounds, bands, protectionlevel, providerproperties)
  }

  @deprecated("Use RasterRDD method instead", "")
  def saveMrsPyramidRDD(tiles:RDD[(TileIdWritable, RasterWritable)], outputProvider:MrsImageDataProvider,
                        zoom:Int, tilesize:Int, nodatas:Array[Double], conf:Configuration, tiletype:Int = -1,
                        bounds:Bounds = null, bands:Int = -1,
                        protectionlevel:String = null,
                        providerproperties:ProviderProperties = new ProviderProperties()):Unit = {

    saveMrsPyramid(RasterRDD(tiles), outputProvider, zoom, tilesize, nodatas, conf, tiletype, bounds, bands,
      protectionlevel, providerproperties)
  }

  @deprecated("Use RasterRDD method instead", "")
  def calculateBounds(rdd:RDD[(TileIdWritable, RasterWritable)], zoom:Int, tilesize:Int):Bounds = {
    calculateBounds(RasterRDD(rdd), zoom, tilesize)
  }

  def calculateMetadata(rdd:RasterRDD, zoom:Int, nodata:Double, calcStats:Boolean,
                        bounds:Bounds):MrsPyramidMetadata = {
    val first = rdd.first()
    val raster = RasterWritable.toMrGeoRaster(first._2)

    val nodatas = Array.fill[Double](raster.bands())(nodata)
    calculateMetadata(rdd, zoom, nodatas, calcStats, bounds)
  }

  def calculateMetadata(rdd:RasterRDD, zoom:Int, nodatas:Array[Double], calcStats:Boolean,
                        bounds:Bounds):MrsPyramidMetadata = {
    val meta = new MrsPyramidMetadata

    //    rdd.persist(StorageLevel.MEMORY_AND_DISK_SER)

    meta.setPyramid(rdd.name)
    meta.setName(zoom)
    meta.setMaxZoomLevel(zoom)

    val first = rdd.first()
    //val raster = RasterWritable.toRaster(first._2)
    val raster = RasterWritable.toMrGeoRaster(first._2)

    meta.setBands(raster.bands())
    meta.setTileType(raster.datatype())

    val tilesize = raster.width()
    meta.setTilesize(tilesize)

    meta.setDefaultValues(nodatas)

    val bnds = if (bounds == null) {
      calculateBounds(rdd, zoom, tilesize)
    }
    else {
      bounds
    }

    meta.setBounds(bnds)

    meta.setName(zoom, zoom.toString)

    val tb = TMSUtils.boundsToTile(bnds, zoom, tilesize)
    meta.setTileBounds(zoom, tb.toLongRectangle)

    val pll:Pixel = TMSUtils.latLonToPixels(bnds.s, bnds.w, zoom, tilesize)
    val pur:Pixel = TMSUtils.latLonToPixels(bnds.n, bnds.e, zoom, tilesize)
    meta.setPixelBounds(zoom, new LongRectangle(0, 0, pur.px - pll.px, pur.py - pll.py))

    if (calcStats) {
      val stats = calculateStats(rdd, meta.getBands, nodatas)

      meta.setImageStats(zoom, stats)
    }

    meta
  }

  def humantokb(human:String):Int = {
    //val pre: Char = new String ("KMGTPE").charAt (exp - 1)
    val trimmed = human.trim.toLowerCase
    val units = trimmed.charAt(trimmed.length - 1)
    val exp = units match {
      case 'k' => 0
      case 'm' => 1
      case 'g' => 2
      case 'p' => 3
      case 'e' => 4
      case _ => return trimmed.substring(0, trimmed.length - 2).toInt
    }

    val mult = Math.pow(1024, exp).toInt

    val v:Int = trimmed.substring(0, trimmed.length - 1).toInt
    v * mult
  }

  def kbtohuman(kb:Long, maxUnit:String = null):String = {
    if (kb == 0) {
      "0"
    }
    else {
      val suffix = "kmgtpe"
      val unit = 1024
      var exp:Int = (Math.log(kb) / Math.log(unit)).toInt

      if (maxUnit != null) {
        val maxexp = suffix.indexOf(maxUnit.trim.toLowerCase)
        if (maxexp > 0 && exp > maxexp) {
          exp = maxexp
        }
      }

      val pre:Char = suffix.charAt(exp)

      "%d%s".format((kb / Math.pow(unit, exp)).toInt, pre)
    }
  }

  def jarForClass(clazz:String, cl:ClassLoader = null):String = {
    // now the hard part, need to look in the dependencies...
    val classFile:String = clazz.replaceAll("\\.", "/") + ".class"

    var iter:java.util.Enumeration[URL] = null

    if (cl != null) {
      iter = cl.getResources(classFile)
    }
    else {
      val cll = getClass.getClassLoader
      iter = cll.getResources(classFile)
    }

    while (iter.hasMoreElements) {
      val url:URL = iter.nextElement
      if (url.getProtocol == "jar") {
        val path:String = url.getPath
        if (path.startsWith("file:")) {
          // strip off the "file:" and "!<classname>"
          return path.substring("file:".length).replaceAll("!.*$", "")
        }
      }
    }

    null
  }

  def jarsForClass(clazz:String, cl:ClassLoader = null):Array[String] = {
    // now the hard part, need to look in the dependencies...
    val classFile:String = clazz.replaceAll("\\.", "/") + ".class"

    jarsForPackage(classFile, cl)
  }

  def jarsForPackage(pkg:String, cl:ClassLoader = null):Array[String] = {
    // now the hard part, need to look in the dependencies...
    var iter:java.util.Enumeration[URL] = null

    val pkgFile:String = pkg.replaceAll("\\.", "/")

    if (cl != null) {
      iter = cl.getResources(pkgFile)
    }
    else {
      val cll = getClass.getClassLoader
      iter = cll.getResources(pkgFile)
    }

    val ab:mutable.ArrayBuilder[String] = mutable.ArrayBuilder.make()
    while (iter.hasMoreElements) {
      val url:URL = iter.nextElement
      if (url.getProtocol == "jar") {
        val path:String = url.getPath
        if (path.startsWith("file:")) {
          // strip off the "file:" and "!<classname>"
          ab += path.substring("file:".length).replaceAll("!.*$", "")
        }
      }
    }

    ab.result()
  }

  // These 3 methods are taken almost verbatim from Spark's Utils class, but they are all
  // private, so we needed to copy them here
  private def loadDefaultSparkProperties(conf:SparkConf, filePath:String = null):String = {
    val path = Option(filePath).getOrElse(getDefaultPropertiesFile())

    Option(path).foreach { confFile => {
      getPropertiesFromFile(confFile).filter { case (k, v) =>
        k.startsWith("spark.")
      }.foreach { case (k, v) =>
        conf.setIfMissing(k, v)
        sys.props.getOrElseUpdate(k, v)
      }
    }
    }
    path
  }

  /** Load properties present in the given file. */
  @SuppressFBWarnings(value = Array("PATH_TRAVERSAL_IN"),
    justification = "only opens files filtered by getDefaultPropertiesFile()")
  private def getPropertiesFromFile(filename:String):Map[String, String] = {
    val file = new File(filename)
    require(file.exists(), s"Properties file $file does not exist")
    require(file.isFile, s"Properties file $file is not a normal file")

    val inReader = new InputStreamReader(new FileInputStream(file), "UTF-8")
    try {
      val properties = new Properties()
      properties.load(inReader)
      properties.stringPropertyNames().map(k => (k, properties(k).trim)).toMap
    }
    catch {
      case e:IOException =>
        throw new SparkException(s"Failed when loading Spark properties from $filename", e)
    }
    finally {
      inReader.close()
    }
  }

  @SuppressFBWarnings(value = Array("PATH_TRAVERSAL_IN"), justification = "opening a hardcoded filename")
  private def getDefaultPropertiesFile(env:Map[String, String] = sys.env):String = {
    env.get("SPARK_CONF_DIR")
        .orElse(env.get("SPARK_HOME").map { t => s"$t${File.separator}conf" })
        .map { t => new File(s"$t${File.separator}spark-defaults.conf") }
        .filter(_.isFile)
        .map(_.getAbsolutePath)
        .orNull
  }


  //  def address(obj: Object): String = {
  //    var addr = "0x"
  //
  //    val array = Array(obj)
  //    val f = classOf[sun.misc.Unsafe].getDeclaredField("theUnsafe")
  //    f.setAccessible(true)
  //    val unsafe = f.get(null).asInstanceOf[sun.misc.Unsafe]
  //
  //
  //    val offset: Long = unsafe.arrayBaseOffset(classOf[Array[Object]])
  //    val scale = unsafe.arrayIndexScale(classOf[Array[Object]])
  //
  //    scale match {
  //    case 4 =>
  //      val factor = 8
  //      val i1 = (unsafe.getInt(array, offset) & 0xFFFFFFFFL) * factor
  //      addr += i1.toHexString
  //    case 8 =>
  //      throw new AssertionError("Not supported")
  //    }
  //
  //    addr
  //  }

}
