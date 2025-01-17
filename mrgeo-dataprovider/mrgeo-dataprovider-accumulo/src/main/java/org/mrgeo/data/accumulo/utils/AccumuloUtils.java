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

package org.mrgeo.data.accumulo.utils;

import org.apache.accumulo.core.client.*;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.client.security.tokens.AuthenticationToken;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.accumulo.core.security.ColumnVisibility;
import org.apache.accumulo.core.util.BadArgumentException;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.Text;
import org.mrgeo.core.MrGeoProperties;
import org.mrgeo.data.DataProviderException;
import org.mrgeo.data.DataProviderFactory;
import org.mrgeo.data.ProviderProperties;
import org.mrgeo.data.raster.RasterWritable;
import org.mrgeo.image.MrsPyramidMetadata;
import org.mrgeo.image.MrsPyramidMetadata.Classification;
import org.mrgeo.utils.LongRectangle;
import org.mrgeo.utils.tms.Bounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.Map.Entry;

public class AccumuloUtils
{
static Logger log = LoggerFactory.getLogger(AccumuloUtils.class);
  /*
   * Start tileId (long) transformation utilities
   */

/**
 * Convert a tile id (long) to a Text object.
 *
 * @param tileId is a long to be put into a text object
 * @return Text object with the bytes being the value of the tileId (long)
 */

public static Text toText(long tileId)
{
  byte[] bytes = ByteBuffer.allocate(8).putLong(tileId).array();
  return new Text(bytes);
} // end toText


/**
 * Convert a Text object of a tileId to a back to a long.
 *
 * @param rowId Text object to convert.
 * @return the long value from the Text object.
 */
public static long toLong(Text rowId)
{

  byte[] outB = new byte[8];
  for (int x = 0; x < outB.length; x++)
  {
    if (x >= rowId.getLength())
    {
      outB[x] = 0x0;
    }
    else
    {
      outB[x] = rowId.getBytes()[x];
    }
  }

  return ByteBuffer.wrap(outB).getLong();
} // end toLong


/**
 * Create a long form a rowId set of bytes.
 *
 * @param rowId - The bytes to convert to a long.
 * @return The long value.
 */
public static long toLong(byte[] rowId)
{
//	  if(rowId.length == 8){
//	    return ByteBuffer.wrap(rowId).getLong();
//	  }
  // make sure there are 8 bytes - 8 are needed for the ByteBuffer.wrap
  byte[] outB = new byte[8];
  for (int x = 0; x < outB.length; x++)
  {
    if (x >= rowId.length)
    {
      outB[x] = 0x0;
    }
    else
    {
      outB[x] = rowId[x];
    }
  }
  return ByteBuffer.wrap(outB).getLong();

}


/**
 * Convert the tileId to Text with a different name.
 *
 * @param tileId is a long to be put into a text object
 * @return Text object with the bytes being the value of the tileId (long)
 */
public static Text toRowId(long tileId)
{
  return toText(tileId);
} // end toRowId


/**
 * Create an Accumulo Key object from the tileId.
 *
 * @param tileId is a long to be put into the rowId position of the Key
 * @return Accumulo Key object.
 */
public static Key toKey(long tileId)
{
  return new Key(toRowId(tileId), new Text(""), new Text(""));
} // end toKey


/**
 * Create an Accumulo Key object from the tileId with more information in the Key.  This
 * is more human readable.
 *
 * @param z      zoom level of the tileId
 * @param tileId is a long to be put into the rowId position of the Key
 * @return Accumulo Key object.
 */
public static Key toKeyFull(int z, long tileId)
{
  return new Key(toRowId(tileId), new Text(Integer.toString(z)), new Text(Long.toString(tileId)));
} // end toKey


	/*
   * done with tileId utilities
	 */

	
	/*
   * Start Raster utilities
	 */


/**
 * Convert a Raster to a Value object.
 *
 * @param raster is the object to convert.
 * @return a Value containing the bytes of the matrix of the original raster.
 * @throws IOException
 */
//	public static Value toValue(Raster raster) throws IOException {
//	  // note the upcast from RasterWritable to Value	  
//		return RasterWritable.toWritable(raster);
//	} // end toValue


/**
 * Convert a Raster to a Value object utilizing the compression codecs.
 *
 * @param raster is the object to convert.
 * @return a Value containing the bytes of the matrix of the original raster.
 * @throws IOException
 */
//	public static Value toValue(Raster raster, CompressionCodec codec, Compressor compressor) throws IOException {
//	  // note the upcast from RasterWritable to Value
//		return RasterWritable.toWritable(raster, codec, compressor);
//	} // end toValue



	
	/*
	 * End Raster utilities
	 */

/**
 * This will take an existing HDFS directory and do a bulk ingest from that directory to the
 * table specified in the call to the function.  It is assumed that the MrGeoProperties is
 * populated with these values:
 * accumulo.instance
 * zooservers
 * accumulo.user
 * accumulo.password
 *
 * @param workDir   is the directory that contains a directory called "files".
 * @param tableName is the destination table.
 * @param conf      is the configuration of the system.
 * @throws TableNotFoundException
 * @throws IOException
 * @throws AccumuloException
 * @throws AccumuloSecurityException
 */
public static void importDirectory(String workDir, String tableName, Configuration conf)
    throws TableNotFoundException, IOException, AccumuloException, AccumuloSecurityException
{

  // prepare the directory to be imported
  Path path = new Path(workDir);
  FileSystem fs = path.getFileSystem(conf);

  // get rid of the failures directory
  Path failures = new Path(workDir, "failures");
  fs.delete(failures, true);

  // make sure the failures directory exists
  fs.mkdirs(new Path(workDir, "failures"));

  // get the connector to Accumulo
  Connector connector = AccumuloConnector.getConnector(
      MrGeoProperties.getInstance().getProperty("accumulo.instance")
      , MrGeoProperties.getInstance().getProperty("zooservers")
      , MrGeoProperties.getInstance().getProperty("accumulo.user")
      , MrGeoProperties.getInstance().getProperty("accumulo.password"));
  if (!connector.tableOperations().exists(tableName))
  {
    try
    {
      connector.tableOperations().create(tableName, true);
    }
    catch (TableExistsException tee)
    {
      log.error("Exception thrown", tee);
    }
  }
  connector.tableOperations().importDirectory(tableName, workDir + "/files", workDir + "/failures", false);

} // end importDirectory

	
	/*
	 * Start Metadata utilities
	 */

/**
 * This method will decode the MrsImagePyramidMetadata object stored in Accumulo.
 *
 * @param table the table to pull metadata from
 * @param conn  is the Accumulo connector
 * @param auths is the authorizations to use when pulling metadata
 * @return the metadata object
 */
@SuppressWarnings("squid:S1166") // Exceptions are caught and ignored.  This is OK
public static MrsPyramidMetadata getMetadataFromTable(String table, Connector conn, String auths)
{
  MrsPyramidMetadata retMeta = null;
  Authorizations authorizations = createAuthorizationsFromDelimitedString(auths);

  Scanner scanner = null;
  try
  {
    scanner = conn.createScanner(table, authorizations);
    Range r = new Range(new Text(MrGeoAccumuloConstants.MRGEO_ACC_METADATA));
    scanner.setRange(r);
    scanner.fetchColumnFamily(new Text("all"));


    for (Entry<Key, Value> entry : scanner)
    {
      System.out.println("Key: " + entry.getKey().toString());
      if (entry.getKey().getColumnFamily().toString().equals("all"))
      {
        retMeta = (MrsPyramidMetadata) Base64Utils.decodeToObject(entry.getValue().toString());
        break;
      }
    }
    if (retMeta == null)
    {
      return buildMetadataFromTable(table, conn, auths);
    }

  }
  catch (TableNotFoundException | IOException | ClassNotFoundException ignored)
  {

  }

  return retMeta;
} // end getMetadataFromTable


/**
 * This method will rebuild the Metadata object from the items stored in an Accumulo table.
 *
 * @param table the table to pull metadata from
 * @param conn  is the Accumulo connector
 * @param auths is the authorizations to use when pulling metadata
 * @return the metadata object
 */
@Deprecated
@SuppressWarnings("squid:S1166") // Exception caught and handled
public static MrsPyramidMetadata buildMetadataFromTable(String table, Connector conn, String auths)
{
  MrsPyramidMetadata retMeta = new MrsPyramidMetadata();
  Authorizations authorizations = createAuthorizationsFromDelimitedString(auths);

  Scanner scanner = null;
  try
  {
    scanner = conn.createScanner(table, authorizations);
    Range r = new Range(new Text(MrGeoAccumuloConstants.MRGEO_ACC_METADATA));
    scanner.setRange(r);


    for (Entry<Key, Value> entry : scanner)
    {
      System.out.println("Key: " + entry.getKey().toString());
      String cf = entry.getKey().getColumnFamily().toString();

      // parse the values
      if (cf.equals("zoomLevel"))
      {
        // looking at the maximum zoom level
        retMeta.setMaxZoomLevel(Integer.parseInt(entry.getValue().toString()));

      }
      else if (cf.equals("bounds"))
      {
        // looking at the geospatial bounds of the data
        Bounds bounds = Bounds.fromCommaString(entry.getValue().toString());
        retMeta.setBounds(bounds);

      }
      else if (cf.equals("tileBounds"))
      {
        // looking at the tile bounds for a zoom level
        int curZL = Integer.parseInt(entry.getKey().getColumnQualifier().toString());
        String[] vals = entry.getValue().toString().split(",");

        LongRectangle lr = new LongRectangle(Long.parseLong(vals[0]),
            Long.parseLong(vals[1]),
            Long.parseLong(vals[2]),
            Long.parseLong(vals[3]));

        retMeta.setTileBounds(curZL, lr);

      }
      else if (cf.equals("pixelBounds"))
      {
        // looking at the pixel bounds
        int curZL = Integer.parseInt(entry.getKey().getColumnQualifier().toString());
        String[] vals = entry.getValue().toString().split(",");

        LongRectangle lr = new LongRectangle(Long.parseLong(vals[0]),
            Long.parseLong(vals[1]),
            Long.parseLong(vals[2]),
            Long.parseLong(vals[3]));

        retMeta.setPixelBounds(curZL, lr);

      }
      else if (cf.equals("bands"))
      {
        // get the bands of the original image
        int b = Integer.parseInt(entry.getValue().toString());
        retMeta.setBands(b);

      }
      else if (cf.equals("classification"))
      {
        // get the classification
        Classification c = Classification.valueOf(entry.getValue().toString());
        retMeta.setClassification(c);

      }
      else if (cf.equals("defaultValues"))
      {
        // parse the double values
        String[] dVals = entry.getValue().toString().split(",");
        double[] dv = new double[dVals.length];
        for (int x = 0; x < dVals.length; x++)
        {
          dv[x] = Double.parseDouble(dVals[x]);
        }
        retMeta.setDefaultValues(dv);

      }
      else if (cf.equals("imageString"))
      {
        int curZL = Integer.parseInt(entry.getKey().getColumnQualifier().toString());
        retMeta.setName(curZL, entry.getValue().toString());

      }
      else if (cf.equals("tileSize"))
      {
        retMeta.setTilesize(Integer.parseInt(entry.getValue().toString()));
      }
      else if (cf.equals("name"))
      {
        retMeta.setPyramid(entry.getValue().toString());
      }

    }


  }
  catch (TableNotFoundException ignored)
  {

  }

  return retMeta;

} // end buildMetadataFromTable


/**
 * Store image metadata into a table.
 *
 * @param table    - the table to use.
 * @param metadata - the metadata object to store.
 * @param conn     - the accumulo connector to use.
 * @param cv       - the ColumnVisibility to use.
 * @return - true if successful in storing the data.
 */
@Deprecated
public static boolean storeMetadataIntoTable(String table, MrsPyramidMetadata metadata, Connector conn, String cv)
{
  ColumnVisibility cViz = null;

  if (cv == null)
  {
    cViz = new ColumnVisibility();
  }
  else
  {
    cViz = new ColumnVisibility(cv);
  }

  return storeMetadataIntoTable(table, metadata, conn, cViz);
} // end storeMetadataIntoTable


/**
 * This is a method for storing a MrsImagePyramidMetadata object into an Accumulo Table
 * <p>
 * The metadata object is base64 encoded to store all items.
 * <p>
 * The individual items of the object are also stored.
 * all - base64 - base64 encoded object
 * zoomLevel - max zoom level - max zoom level
 * bounds - max zoom level - geo spatial bounds seperated by commas
 * tileBounds - zoom level - tile bounds separated by commas
 * pixelBounds - zoom level - pixel bounds of original image
 * bands - bands of original image - bands of original image
 * classification - classification value - classification value
 * defaultValues - max zoom level - double array separated by commas
 * imageString - max zoom level - the string pointing to the image
 * tileSize - max zoom level - the size of the tile
 * //stats - zoom level - stats values separated by commas
 *
 * @param table    - table to be used for the storage of the information
 * @param metadata - the MrsImagePyramidMetadata object to be stored
 * @param conn     - the Accumulo connector to use
 */
@SuppressWarnings("squid:S1166") // Exceptions are caught and returned as false
public static boolean storeMetadataIntoTable(String table, MrsPyramidMetadata metadata, Connector conn,
    ColumnVisibility cViz)
{
  ColumnVisibility columnVis;
  if (cViz != null)
  {
    columnVis = cViz;
  }
  else
  {
    columnVis = new ColumnVisibility();
  }
  int zoomLevel = metadata.getMaxZoomLevel();

  try
  {
    BatchWriter bw = conn.createBatchWriter(table, 1000, Long.MAX_VALUE, 1);
    //ColumnVisibility cv = new ColumnVisibility();

    // store all metadata base 64 encoded
    Mutation ma = new Mutation(MrGeoAccumuloConstants.MRGEO_ACC_METADATA);
    ma.put("all", "base64", columnVis, new Value(Base64Utils.encodeObject(metadata).getBytes()));
    bw.addMutation(ma);

    // store the zoom level
    Mutation mz = new Mutation(MrGeoAccumuloConstants.MRGEO_ACC_METADATA);
    mz.put("zoomLevel", "" + zoomLevel, columnVis, new Value(("" + zoomLevel).getBytes()));
    bw.addMutation(mz);

    // store the geospatial bounds
    Mutation mb = new Mutation(MrGeoAccumuloConstants.MRGEO_ACC_METADATA);
    mb.put("bounds", "" + zoomLevel, columnVis, new Value(metadata.getBounds().toCommaString().getBytes()));
    bw.addMutation(mb);

    // store the tile bounds for the best zoom level
    Mutation mtb = new Mutation(MrGeoAccumuloConstants.MRGEO_ACC_METADATA);
    mtb.put("tileBounds", "" + zoomLevel, columnVis,
        new Value(metadata.getTileBounds(zoomLevel).toDelimitedString().getBytes()));
    bw.addMutation(mtb);

    // store the pixel bounds of the image
    Mutation mpb = new Mutation(MrGeoAccumuloConstants.MRGEO_ACC_METADATA);
    mpb.put("pixelBounds", "" + zoomLevel, columnVis,
        new Value(metadata.getPixelBounds(zoomLevel).toDelimitedString().getBytes()));
    bw.addMutation(mpb);

    // store the bands of the original object
    int bands = metadata.getBands();
    Mutation mband = new Mutation(MrGeoAccumuloConstants.MRGEO_ACC_METADATA);
    mband.put("bands", "" + zoomLevel, columnVis, new Value(Integer.toString(bands).getBytes()));
    bw.addMutation(mband);

    // store the classification
    Classification cl = metadata.getClassification();
    Mutation mclass = new Mutation(MrGeoAccumuloConstants.MRGEO_ACC_METADATA);
    mclass.put("classification", "" + zoomLevel, columnVis, new Value(cl.toString().getBytes()));
    bw.addMutation(mclass);

    double[] dVals = metadata.getDefaultValues();
    StringBuffer sb = new StringBuffer();
    for (int x = 0; x < dVals.length; x++)
    {
      sb.append(dVals[x]);
      if (x < dVals.length - 1)
      {
        sb.append(",");
      }
    }
    Mutation mdVals = new Mutation(MrGeoAccumuloConstants.MRGEO_ACC_METADATA);
    mdVals.put("defaultValues", "" + zoomLevel, columnVis, new Value(sb.toString().getBytes()));
    bw.addMutation(mdVals);

    String imgStr = metadata.getName(zoomLevel);
    Mutation mimg = new Mutation(MrGeoAccumuloConstants.MRGEO_ACC_METADATA);
    mimg.put("imageString", "" + zoomLevel, columnVis, new Value(imgStr.getBytes()));
    bw.addMutation(mimg);

    int tsize = metadata.getTilesize();
    Mutation mts = new Mutation(MrGeoAccumuloConstants.MRGEO_ACC_METADATA);
    mts.put("tileSize", "" + zoomLevel, columnVis, new Value(Integer.toString(tsize).getBytes()));
    bw.addMutation(mts);

    String name = metadata.getPyramid();
    Mutation mname = new Mutation(MrGeoAccumuloConstants.MRGEO_ACC_METADATA);
    mname.put("name", "" + zoomLevel, columnVis, new Value(name.getBytes()));
    bw.addMutation(mname);

    //ImageStats[] sts = metadata.getStats();

    bw.flush();
    bw.close();
  }
  catch (IOException | MutationsRejectedException | TableNotFoundException ignored)
  {
    return false;
  }
  return true;
} // end putMetadataIntoTable

	/*
	 * End Metadata utilities
	 */

/**
 * Get a single raster
 *
 * @param table this is the table containing the raster
 * @param tid   the tile id to get
 * @param zl    the zoom level of the raster
 * @param conn  the Accumulo connector to use
 * @param auths the authorizations to use for access
 * @return
 */
@Deprecated
@SuppressWarnings("squid:S1166") // TableNotFoundException are caught and ignored.  This is OK
public static RasterWritable getRaster(String table, long tid, int zl, Connector conn, String auths)
{
  RasterWritable retRaster = null;
  Authorizations authorizations = createAuthorizationsFromDelimitedString(auths);

  Scanner scanner = null;
  try
  {
    scanner = conn.createScanner(table, authorizations);


    Range r = new Range(toRowId(tid), toRowId(tid + 1));
    scanner.setRange(r);
    scanner.fetchColumnFamily(new Text(Integer.toString(zl).getBytes()));


    for (Entry<Key, Value> entry : scanner)
    {
      System.out.println("Key: " + entry.getKey().toString());

      DataInputBuffer dib = new DataInputBuffer();
      byte[] data = entry.getValue().get();
      dib.reset(data, data.length);

      retRaster = new RasterWritable();
      retRaster.readFields(dib);
      break;
    }


  }
  catch (TableNotFoundException ignored)
  {

  }
  catch (IOException e)
  {
    log.error("Exception thrown", e);
  }

  return retRaster;
} // getRaster


public static Set<String> getListOfTables(Properties properties) throws DataProviderException
{

  Properties props = AccumuloConnector.getAccumuloProperties();
  if (properties != null)
  {
    props.putAll(properties);
    String userRoles = properties.getProperty(DataProviderFactory.PROVIDER_PROPERTY_USER_ROLES);
    if (userRoles != null)
    {
      props.setProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_AUTHS, userRoles);
    }
  }

  Connector conn;
  try
  {
    //conn = AccumuloConnector.getConnector();
    Instance inst = new ZooKeeperInstance(
        props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_INSTANCE),
        props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_ZOOKEEPERS));
    AuthenticationToken token = new PasswordToken(
        props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_PASSWORD).getBytes());
    conn = inst.getConnector(
        props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_USER),
        token);
  }
  catch (AccumuloSecurityException | AccumuloException e)
  {
    log.error("Exception thrown", e);
    return null;
  }
  //catch (DataProviderException dpe) {
  //dpe.printStackTrace();
  //return null;
  //}
//		TCredentials tcr = new TCredentials();
//		tcr.setInstanceId(props
//				.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_INSTANCE));
//		tcr.setPrincipal(props
//				.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_USER));
//		tcr.setToken(props.getProperty(
//				MrGeoAccumuloConstants.MRGEO_ACC_KEY_PASSWORD).getBytes());
//
//		TableOperationsImpl toi = new TableOperationsImpl(conn.getInstance(), tcr);
  Set<String> list = conn.tableOperations().list();


  //Set<String> list = toi.list();
  ArrayList<String> ignoreTables = new ArrayList<String>();
  if (props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_EXCLUDETABLES) != null)
  {
    ignoreTables
        .addAll(Arrays.asList(props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_EXCLUDETABLES).split(",")));
  }

  Set<String> retList = new HashSet<String>();
  for (String l : list)
  {
    if (l.equals("!METADATA") ||
        l.equals("trace") ||
        l.equals("accumulo.root") ||
        l.equals("accumulo.metadata") ||
        ignoreTables.contains(l))
    {
      continue;
    }

    retList.add(l);
  }

  return retList;

} // end getListOfTables

public static ArrayList<String> getIgnoreTables() throws DataProviderException
{
  Properties props = AccumuloConnector.getAccumuloProperties();
  ArrayList<String> ignoreTables = new ArrayList<String>();
  ignoreTables.add("!METADATA");
  ignoreTables.add("trace");
  ignoreTables.add("accumulo.root");
  ignoreTables.add("accumulo.metadata");
  if (props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_EXCLUDETABLES) != null)
  {
    ignoreTables
        .addAll(Arrays.asList(props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_EXCLUDETABLES).split(",")));
  }
  return ignoreTables;
} // end


public static Hashtable<String, String> getGeoTables(Properties providerProperties)
    throws DataProviderException
{

  Properties props = AccumuloConnector.getAccumuloProperties();
  if (providerProperties != null)
  {
    props.putAll(providerProperties);
  }

  String strAuths = props.getProperty(DataProviderFactory.PROVIDER_PROPERTY_USER_ROLES);
  if (strAuths == null)
  {
    strAuths = props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_AUTHS);
  }

  Authorizations auths = createAuthorizationsFromDelimitedString(strAuths);
  Connector conn;
  try
  {
    //conn = AccumuloConnector.getConnector();
    Instance inst = new ZooKeeperInstance(
        props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_INSTANCE),
        props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_ZOOKEEPERS));
    AuthenticationToken token = new PasswordToken(
        props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_PASSWORD).getBytes());
    conn = inst.getConnector(
        props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_USER),
        token);
  }
  catch (AccumuloSecurityException | AccumuloException ase)
  {
    log.error("Exception thrown", ase);
    return null;
  }
  //catch (DataProviderException dpe) {
  //dpe.printStackTrace();
  //return null;
  //}

//			TCredentials tcr = new TCredentials();
//			tcr.setInstanceId(props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_INSTANCE));
//			tcr.setPrincipal(props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_USER));
//			tcr.setToken(props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_PASSWORD).getBytes());

  AuthenticationToken at =
      new PasswordToken(props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_PASSWORD).getBytes());

  return getGeoTables(props.getProperty(MrGeoAccumuloConstants.MRGEO_ACC_KEY_EXCLUDETABLES), at, auths, conn);
} // end getGeoTables


public static Hashtable<String, String> getGeoTables(String ignore, AuthenticationToken token,
    Authorizations auths, Connector conn)
{
//	public static Hashtable<String, String> getGeoTables(TCredentials tcr,
//			Authorizations auths, Connector conn) {
  if (auths == null || conn == null)
  {
    return null;
  }
  Hashtable<String, String> retTable = new Hashtable<String, String>();
  ArrayList<String> ignoreTables = new ArrayList<String>();
  if (ignore != null)
  {
    ignoreTables.addAll(Arrays.asList(ignore.split(",")));
  }

  //TableOperationsImpl toi = new TableOperationsImpl(conn.getInstance(), tcr);

//		Credentials cred = new Credentials("root", token);
//		org.apache.accumulo.core.client.impl.TableOperationsImpl toi = 
//				new org.apache.accumulo.core.client.impl.TableOperationsImpl(conn.getInstance(), cred);
//		Set<String> list = toi.list();

  Set<String> list = conn.tableOperations().list();
  for (String l : list)
  {
    if (l.equals("!METADATA") ||
        l.equals("trace") ||
        l.equals("accumulo.root") ||
        l.equals("accumulo.metadata") ||
        ignoreTables.contains(l))
    {
      continue;
    }
    //System.out.println("Looking at table: " + l);
    // log.info("Looking at table: " + l);
    Scanner scann = null;
    try
    {
      scann = conn.createScanner(l, auths);
      String m1 = "METADATA";
      String m2 = "METADATB";
      Range r = new Range(m1, m2);

      scann.setRange(r);

      Iterator<Entry<Key, Value>> it = scann.iterator();
      while (it.hasNext())
      {

        Entry<Key, Value> e = it.next();
        // log.info("building list of tables: " +
        // e.getKey().toString());
//					String row = e.getKey().getRow().toString();
//					String cf = e.getKey().getColumnFamily().toString();
//					String cq = e.getKey().getColumnQualifier().toString();
//					String v = e.getValue().toString();

        if (e.getKey().getRow().toString()
            .equals(MrGeoAccumuloConstants.MRGEO_ACC_METADATA)
            && e.getKey()
            .getColumnFamily()
            .toString()
            .equals(MrGeoAccumuloConstants.MRGEO_ACC_METADATA)
            && e.getKey()
            .getColumnQualifier()
            .toString()
            .equals(MrGeoAccumuloConstants.MRGEO_ACC_CQALL))
        {
          Text vis = e.getKey().getColumnVisibility();
          // log.info("Adding " + l +
          // " to list of available tables.");

          if (vis != null)
          {
            // ADPF_ImageToTable.put(l, vis.toString());
            retTable.put(l, vis.toString());
          }
          else
          {
            // ADPF_ImageToTable.put(l, "");
            retTable.put(l, "");
          }
        }
      } // end while

      scann.clearColumns();

    }
    catch (Exception e)
    {
      log.error("Exception thrown", e);
    }

  } // end for loop

  return retTable;
} // end getGeoTables

/**
 * Wrapper around ColumnVisibility to make sure that protection
 * levels are valid.
 *
 * @param protectionLevel
 * @return
 */
@SuppressWarnings({"squid:S1166", "squid:S1848"})
// BadArgumentException exception is caught and returned as false, Using object creation for valid check
public static boolean validateProtectionLevel(final String protectionLevel)
{
  try
  {
    new ColumnVisibility(protectionLevel);
  }
  catch (BadArgumentException ignored)
  {
    return false;
  }

  return true;
} // end validateProtectionLevel

/**
 * This is a backward compatibility method. The data provider API used to pass
 * provider properties as a Properties object. It now uses a specialized class
 * called ProviderProperties. This method converts back to the Properties
 * object to prevent having to change a bunch of code in the Accumulo data provider.
 * TODO: it would be best to change the data provider at some point to
 * get rid of this inefficiency.
 *
 * @param providerProperties
 * @return
 */
public static Properties providerPropertiesToProperties(ProviderProperties providerProperties)
{
  if (providerProperties == null)
  {
    return null;
  }

  Properties p = new Properties();
  p.setProperty(DataProviderFactory.PROVIDER_PROPERTY_USER_NAME,
      providerProperties.getUserName());
  p.setProperty(DataProviderFactory.PROVIDER_PROPERTY_USER_ROLES,
      StringUtils.join(providerProperties.getRoles(), ","));
  return p;
}

public static Authorizations createAuthorizationsFromDelimitedString(String auths)
{
  Authorizations authorizations = null;
  if (auths == null || auths.isEmpty())
  {
    authorizations = new Authorizations();
  }
  else
  {
    authorizations = new Authorizations(auths.split(","));
  }
  return authorizations;
}

} // end AccumuloUtils
