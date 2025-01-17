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

package org.mrgeo.services.tms;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.mrgeo.data.DataProviderFactory;
import org.mrgeo.data.ProviderProperties;
import org.mrgeo.image.MrsPyramid;
import org.mrgeo.image.MrsPyramidMetadata;
import org.mrgeo.services.SecurityUtils;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author Steve Ingram
 *         Date: 11/1/13
 */
@Singleton
public class TmsService
{

private LoadingCache<String, MrsPyramidMetadata> metadataCache;

public TmsService()
{
  metadataCache = CacheBuilder.newBuilder()
      .maximumSize(1000)
      .expireAfterAccess(10, TimeUnit.MINUTES)
      .build(
          new CacheLoader<String, MrsPyramidMetadata>()
          {
            @Override
            public MrsPyramidMetadata load(String raster) throws Exception
            {
              return getPyramid(raster).getMetadata();
            }
          });
}

@SuppressWarnings("static-method")
public MrsPyramid getPyramid(String raster) throws IOException
{
  return MrsPyramid.open(raster, (ProviderProperties) null);
}

public MrsPyramidMetadata getMetadata(String raster) throws ExecutionException
{
  return metadataCache.get(raster);
}

public List<String> listImages() throws IOException
{
  return Arrays.asList(DataProviderFactory.listImages(SecurityUtils.getProviderProperties()));
}
}
