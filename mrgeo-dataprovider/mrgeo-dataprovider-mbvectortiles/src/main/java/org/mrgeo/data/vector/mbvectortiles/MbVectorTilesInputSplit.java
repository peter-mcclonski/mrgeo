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

package org.mrgeo.data.vector.mbvectortiles;

import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.InputSplit;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class MbVectorTilesInputSplit extends InputSplit implements Writable
{
  private long offset = 0;
  private long limit = 0;
  private int zoomLevel = -1;

  public MbVectorTilesInputSplit()
  {
  }

  public MbVectorTilesInputSplit(long offset, long limit, int zoomLevel)
  {
    this.offset = offset;
    this.limit = limit;
    this.zoomLevel = zoomLevel;
  }

  public long getOffset() { return offset; }
  public long getLimit() { return limit; }
  public int getZoomLevel() { return zoomLevel; }

  @Override
  public long getLength() throws IOException, InterruptedException
  {
    return offset - limit;
  }

  @Override
  public String[] getLocations() throws IOException, InterruptedException
  {
    return new String[0];
  }

  @Override
  public void write(DataOutput out) throws IOException
  {
    out.writeLong(offset);
    out.writeLong(limit);
    out.writeInt(zoomLevel);
  }

  @Override
  public void readFields(DataInput in) throws IOException
  {
    offset = in.readLong();
    limit = in.readLong();
    zoomLevel = in.readInt();
  }
}
