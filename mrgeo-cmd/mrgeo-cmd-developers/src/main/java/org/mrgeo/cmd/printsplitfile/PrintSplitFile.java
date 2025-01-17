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

package org.mrgeo.cmd.printsplitfile;

import org.apache.commons.cli.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.mrgeo.cmd.Command;
import org.mrgeo.cmd.MrGeo;
import org.mrgeo.data.DataProviderFactory;
import org.mrgeo.data.ProviderProperties;
import org.mrgeo.data.image.MrsImageDataProvider;
import org.mrgeo.hdfs.image.HdfsMrsImageDataProvider;
import org.mrgeo.hdfs.tile.FileSplit;
import org.mrgeo.hdfs.tile.SplitInfo;
import org.mrgeo.image.MrsPyramidMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * A utility class to print split files on the command line
 * <p>
 * PrintSplitFile <split filename>
 **/

public class PrintSplitFile extends Command
{
private static Logger log = LoggerFactory.getLogger(PrintSplitFile.class);

@Override
public String getUsage() { return "printsplits <options>"; }

@Override
public void addOptions(Options options)
{
  final Option zoom = new Option("z", "zoom", true, "Zoom level");
  zoom.setRequired(false);
  options.addOption(zoom);

  final Option regen = new Option("r", "regenerate", false, "Regenerate Splits");
  regen.setRequired(false);
  options.addOption(regen);
}

@Override
public int run(final CommandLine line, final Configuration conf,
    final ProviderProperties providerProperties) throws ParseException
{
  try
  {
    int zoomlevel = -1;
    if (line.hasOption("z"))
    {
      zoomlevel = Integer.parseInt(line.getOptionValue("z"));
    }

    boolean renerate = line.hasOption("r");


    for (final String name : line.getArgs())
    {
      MrsImageDataProvider dp =
          DataProviderFactory.getMrsImageDataProvider(name, DataProviderFactory.AccessMode.READ,
              conf);

      if (!(dp instanceof HdfsMrsImageDataProvider))
      {
        System.out.println("PrintSplitFile only works on HDFS images");
        return -1;
      }

      MrsPyramidMetadata metadata = dp.getMetadataReader().read();

      if (zoomlevel <= 0)
      {
        for (zoomlevel = metadata.getMaxZoomLevel(); zoomlevel > 0; zoomlevel--)
        {
          if (renerate)
          {
            FileSplit split = new FileSplit();

            Path parent = new Path(new Path(dp.getResourceName()), "" + zoomlevel);

            System.out.println("Regenerating splits for " + parent.toString());

            split.generateSplits(parent, conf);
            split.writeSplits(parent);
          }

          System.out.println("Zoom level: " + zoomlevel);
          printlevel(dp, zoomlevel);
          System.out.println("---------------");
        }
      }
      else
      {
        if (renerate)
        {
          FileSplit split = new FileSplit();

          Path parent = new Path(new Path(dp.getResourceName()), "" + zoomlevel);
          System.out.println("Regenerating splits for " + parent.toString());

          split.generateSplits(parent, conf);
          split.writeSplits(parent);
        }

        printlevel(dp, zoomlevel);
      }
    }
  }
  catch (IOException e)
  {
    log.error("Exception thrown", e);
  }

  return 0;
}

private void printlevel(MrsImageDataProvider dp, int zoomlevel) throws IOException
{
  Path parent = new Path(new Path(dp.getResourceName()), "" + zoomlevel);
  FileSplit fs = new FileSplit();

  String splitname = fs.findSplitFile(parent);
  System.out.println("split file: " + splitname);

  fs.readSplits(parent);
  System.out.println("min\tmax\tname\t\tpartition");

  SplitInfo[] splits = fs.getSplits();
  for (SplitInfo split : splits)
  {
    System.out.print(((FileSplit.FileSplitInfo) split).getStartId());
    System.out.print("\t");
    System.out.print(((FileSplit.FileSplitInfo) split).getEndId());
    System.out.print("\t");
    System.out.print(((FileSplit.FileSplitInfo) split).getName());
    System.out.print("\t");
    System.out.println(split.getPartition());
  }
}
}
