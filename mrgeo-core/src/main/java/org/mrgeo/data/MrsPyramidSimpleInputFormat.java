/*
 * Copyright 2009-2015 DigitalGlobe, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.mrgeo.data;

import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.mrgeo.data.tile.TileIdWritable;
import org.mrgeo.data.tile.TiledInputFormatContext;
import org.mrgeo.image.MrsImagePyramid;
import org.mrgeo.mapreduce.splitters.MrsPyramidInputSplit;
import org.mrgeo.mapreduce.splitters.TiledInputSplit;
import org.mrgeo.pyramid.MrsPyramid;
import org.mrgeo.utils.Bounds;
import org.mrgeo.utils.TMSUtils;

import java.io.IOException;
import java.util.*;

/**
 * This class is the base class for the Hadoop InputFormat classes that are configured
 * into Hadoop jobs submitted by MrGeo for processing pyramid data. Map/reduce jobs
 * on pyramid input data allow multiple inputs. In order to make that work, this class
 * ensures that each split contains the bounds of the splits that are ordered before
 * them and the bounds of the splits that come after them. This way, the RecordReader
 * classes for pyramid data can use that information to ensure that tiles are only
 * ever read once, regardless of which input pyramids contain data in that tile.
 */
public abstract class MrsPyramidSimpleInputFormat<V> extends InputFormat<TileIdWritable, V>
{
  public MrsPyramidSimpleInputFormat()
  {
  }

  /**
   * Sub-classes must override this method so that the data access layer being used can
   * return the native splits for that specific data format.
   *
   * @param context
   * @param ifContext
   * @param input
   * @return
   * @throws IOException
   * @throws InterruptedException
   */
  protected abstract List<TiledInputSplit> getNativeSplits(final JobContext context,
                                                           final TiledInputFormatContext ifContext,
                                                           final String input) throws IOException, InterruptedException;

  /**
   * Returns the list of MrsPyramidInputSplit objects for the input pyramid.
   * Sub-classes should have no need to override this method. It
   * contains logic required by all input formats (described in the overview
   * for this class).
   */
  @Override
  public List<InputSplit> getSplits(JobContext context) throws IOException, InterruptedException
  {
    // Get the TiledInputFormatContext from the JobContext
    TiledInputFormatContext ifContext = TiledInputFormatContext.load(context.getConfiguration());
    String input = ifContext.getInput();

    MrsPyramid p = MrsImagePyramid.open(input, context.getConfiguration());
    String pyramid = p.getName();
    int zoom = ifContext.getZoomLevel();
    List<TiledInputSplit> nativeSplits = getNativeSplits(context, ifContext, pyramid);
    List<TiledInputSplit> filteredSplits = filterInputSplits(ifContext,
              nativeSplits, zoom,
              p.getTileSize());

    List<InputSplit> results = new LinkedList<InputSplit>();
    // remove the current bounds from the post bounds list.
    // Loop the native splits from this input, and create a new MrsPyramidInputSplit
    // that wraps it and includes the pre/post bounds.
    if (filteredSplits != null)
    {
      Iterator<TiledInputSplit> iter = filteredSplits.iterator();
      while (iter.hasNext())
      {
        TiledInputSplit tiledSplit = iter.next();
        MrsPyramidInputSplit mpsplit = new MrsPyramidInputSplit(tiledSplit, p.getName());
        results.add(mpsplit);
      }
    }

    return results;
  }

  /**
   * Performs cropping of input splits to the bounds specified in the ifContext. This
   * logic is common to all pyramid input formats, regardless of the data provider,
   * so there should be no need to override it in sub-classes.
   *
   * @param ifContext
   * @param splits
   * @param zoomLevel
   * @param tileSize
   * @return
   */
  List<TiledInputSplit> filterInputSplits(final TiledInputFormatContext ifContext,
                                          final List<TiledInputSplit> splits,
                                          final int zoomLevel,
                                          final int tileSize)
  {
    // If there are no splits or no crop region, just return the splits
    if (splits.size() == 0 || ifContext.getBounds() == null)
    {
      return splits;
    }
    List<TiledInputSplit> result = new ArrayList<TiledInputSplit>();
    TMSUtils.TileBounds cropBounds = TMSUtils.boundsToTile(TMSUtils.Bounds.asTMSBounds(ifContext.getBounds()),
            ifContext.getZoomLevel(), tileSize);

    SplitIterator splitIter = new SplitIterator(splits, new RegionSplitVisitor(cropBounds));
    TiledInputSplit split = splitIter.next();
    while (split != null)
    {
      result.add(split);
      split = splitIter.next();
    }
    return result;
  }
}
