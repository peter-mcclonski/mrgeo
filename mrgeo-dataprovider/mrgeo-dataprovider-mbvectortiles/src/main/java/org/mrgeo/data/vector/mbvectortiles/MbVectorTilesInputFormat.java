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

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.*;
import org.mrgeo.data.vector.FeatureIdWritable;
import org.mrgeo.geometry.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MbVectorTilesInputFormat extends InputFormat<FeatureIdWritable, Geometry>
{
  private static Logger log = LoggerFactory.getLogger(MbVectorTilesInputFormat.class);
  private MbVectorTilesSettings dbSettings;
  private int zoomLevel = -1;

  public MbVectorTilesInputFormat(MbVectorTilesSettings dbSettings)
  {
    this.dbSettings = dbSettings;
  }

  @Override
  public List<InputSplit> getSplits(JobContext context) throws IOException, InterruptedException
  {
    zoomLevel = dbSettings.getZoom();
    if (zoomLevel < 0) {
      // Get the max zoom from the tile data
      SQLiteConnection conn = null;
      try {
        conn = MbVectorTilesDataProvider.getDbConnection(dbSettings,
                context.getConfiguration());
        String query = "SELECT MAX(zoom_level) FROM tiles";
        SQLiteStatement stmt = null;
        try {
          stmt = conn.prepare(query, false);
          if (stmt.step()) {
            zoomLevel = stmt.columnInt(0);
          }
          else {
            throw new IOException("Unable to get the max zoom level of " + dbSettings.getFilename());
          }
        }
        finally {
          if (stmt != null) {
            stmt.dispose();
          }
        }
      }
      catch(SQLiteException e) {
        throw new IOException("Unable to query " + dbSettings.getFilename() + " for the max zoom level", e);
      }
      finally {
        if (conn != null) {
          conn.dispose();
        }
      }
    }
    long recordCount = getRecordCount(context.getConfiguration());
    long recordsPerPartition = dbSettings.getTilesPerPartition();
    long numPartitions = recordCount / recordsPerPartition;
    if (numPartitions * recordsPerPartition < recordCount) {
      numPartitions += 1;
    }
    List<InputSplit> splits = new ArrayList<InputSplit>();
    for (int i=0; i < numPartitions; i++) {
      MbVectorTilesInputSplit split = new MbVectorTilesInputSplit(i * recordsPerPartition, recordsPerPartition, zoomLevel);
      splits.add(split);
    }
    return splits;
  }

  @Override
  public RecordReader<FeatureIdWritable, Geometry> createRecordReader(InputSplit split, TaskAttemptContext context) throws IOException, InterruptedException
  {
    MbVectorTilesRecordReader reader = new MbVectorTilesRecordReader(dbSettings);
    reader.initialize(split, context);
    return reader;
  }

//  @SuppressFBWarnings(value = {"SQL_INJECTION_JDBC", "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING"}, justification = "User supplied queries are a requirement")
  protected long getRecordCount(Configuration conf) throws IOException
  {
    String countQuery = "SELECT COUNT(*) FROM tiles WHERE zoom_level=?";
    // Run the count query and grab the result.
    SQLiteConnection conn = null;
    try {
      conn = MbVectorTilesDataProvider.getDbConnection(dbSettings, conf);
      SQLiteStatement stmt = null;
      try {
        stmt = conn.prepare(countQuery, false);
        stmt.bind(1, zoomLevel);
        if (stmt.step()) {
          return stmt.columnLong(0);
        }
        else {
          throw new IOException("Unable to count tiles for zoom " + zoomLevel + " in " + dbSettings.getFilename());
        }
      }
      finally {
        if (stmt != null) {
          stmt.dispose();
        }
      }
    }
    catch (SQLiteException e)
    {
      String msg = "Unable to get the count of records using query: " + countQuery;
      log.error(msg, e);
      throw new IOException(msg, e);
    }
    finally {
      if (conn != null) {
        conn.dispose();
      }
    }
  }
}
