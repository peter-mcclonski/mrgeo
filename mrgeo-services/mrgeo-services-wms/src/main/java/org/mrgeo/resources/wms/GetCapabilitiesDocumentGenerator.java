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

package org.mrgeo.resources.wms;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.ArrayUtils;
import org.mrgeo.colorscale.ColorScale;
import org.mrgeo.colorscale.ColorScaleManager;
import org.mrgeo.core.MrGeoConstants;
import org.mrgeo.data.image.MrsImageDataProvider;
import org.mrgeo.image.MrsImage;
import org.mrgeo.image.MrsPyramid;
import org.mrgeo.image.MrsPyramidMetadata;
import org.mrgeo.services.Version;
import org.mrgeo.services.mrspyramid.rendering.ImageHandlerFactory;
import org.mrgeo.services.mrspyramid.rendering.ImageRenderer;
import org.mrgeo.services.utils.RequestUtils;
import org.mrgeo.utils.FloatUtils;
import org.mrgeo.utils.LatLng;
import org.mrgeo.utils.XmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Generates XML output for OGC WMS GetCapabilities request
 */
class GetCapabilitiesDocumentGenerator
{
private static final Logger log = LoggerFactory.getLogger(GetCapabilitiesDocumentGenerator.class);

/*
 * Adds OGC metadata elements to the the parent element
 */
private static void addHttpElement(Element parent, String requestUrl, Version version)
{
  Element http = XmlUtils.createElement(parent, "HTTP");
  Element get = XmlUtils.createElement(http, "Get");
  if (version.isLess("1.4.0"))
  {
    Element onlineResource = XmlUtils.createElement(get, "OnlineResource");
    onlineResource.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
    onlineResource.setAttribute("xlink:type", "simple");
    onlineResource.setAttribute("xlink:href", requestUrl);
  }
  else
  {
    XmlUtils.createTextElement2(get, "OnlineResource", requestUrl);
  }

  Element post = XmlUtils.createElement(http, "Post");
  if (version.isLess("1.4.0"))
  {
    Element onlineResource = XmlUtils.createElement(post, "OnlineResource");
    onlineResource.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
    onlineResource.setAttribute("xlink:type", "simple");
    onlineResource.setAttribute("xlink:href", requestUrl);
  }
  else
  {
    XmlUtils.createTextElement2(post, "OnlineResource", requestUrl);
  }
}

/**
 * Generates an XML document for a DescribeTiles request
 *
 */
Document generateDoc(Version version, String requestUrl, MultivaluedMap<String, String> allParams,
    MrsImageDataProvider[] pyramidFiles) throws IOException, InterruptedException,
    ParserConfigurationException
{
  Document doc;
  DocumentBuilderFactory dBF = DocumentBuilderFactory.newInstance();
  dBF.setValidating(true);

  DocumentBuilder builder = dBF.newDocumentBuilder();
  doc = builder.newDocument();

  Element wmc = doc.createElement("WMT_MS_Capabilities");
  wmc.setAttribute("version", version.toString());
  wmc.setAttribute("updateSequence", "0");
  if (version.isEqual("1.3.0"))
  {
    wmc.setAttribute("xmlns", "http://www.opengis.net/wms");
    wmc.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
    wmc.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
    wmc.setAttribute("xsi:schemaLocation",
        "http://www.opengis.net/wms http://schemas.opengis.net/wms/1.3.0/capabilities_1_3_0.xsd");
  }
  // 1.4.0 isn't out yet, but it does have a preliminary protocol for tiling.
  // We're using that.
  if (version.isEqual("1.4.0"))
  {
    doc.createComment("1.4.0 isn't out yet, but it does have a preliminary protocol for tiling.");
    doc.createComment("See http://www.opengeospatial.org/standards/wms for details.");
    wmc.setAttribute("xmlns", "http://www.opengis.net/wms");
    wmc.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
    wmc.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
    wmc.setAttribute("xsi:schemaLocation",
        "http://www.opengis.net/wms http://schemas.opengis.net/wms/1.4.0/capabilities_1_4_0.xsd");
  }
  doc.appendChild(wmc);
  // //
  // Service
  // //
  Element service = XmlUtils.createElement(wmc, "Service");
  wmc.appendChild(service);
  // WMT Defined
  XmlUtils.createTextElement2(service, "Name", "OGC:WMS");
  XmlUtils.createTextElement2(service, "Title", "MrGeo Web Map Service");
  XmlUtils.createTextElement2(service, "Abstract", "MrGeo Web Map Service");

  // url is just scheme:hostname:port
  String hostUri;

  try
  {
    URI request = new URI(requestUrl);
    hostUri = new URI(request.getScheme(), null,
        request.getHost(), request.getPort(), null, null, null).toASCIIString();
  }
  catch (URISyntaxException e)
  {
    hostUri = requestUrl;
  }

  if (version.isLess("1.4.0"))
  {
    Element onlineResource = XmlUtils.createElement(service, "OnlineResource");
    onlineResource.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
    onlineResource.setAttribute("xlink:type", "simple");
    onlineResource.setAttribute("xlink:href", hostUri);
  }
  else
  {
    XmlUtils.createTextElement2(service, "OnlineResource", hostUri);
  }

  XmlUtils.createTextElement2(service, "Fees", "none");
  XmlUtils.createTextElement2(service, "AccessConstraints", "none");

  String requestStr = requestUrl + (version.isLess("1.3.0") ? "" : "?");

  // //
  // Capability
  // //
  Element capability = XmlUtils.createElement(wmc, "Capability");

  // Request
  Element requestTag = XmlUtils.createElement(capability, "Request");
  // GetCapabilities
  Element getCapabilities = XmlUtils.createElement(requestTag, "GetCapabilities");
  XmlUtils.createTextElement2(getCapabilities, "Format", "application/vnd.ogc.wms_xml");
  Element gcDcpType = XmlUtils.createElement(getCapabilities, "DCPType");
  addHttpElement(gcDcpType, requestUrl, version);

  addCapability(requestTag, "GetMap", version, requestStr);
  addCapability(requestTag, "GetMosaic", version, requestStr);

  // Tiled extensions
  if (!version.isLess("1.4.0"))
  {
    // DescribeTiles request
    Element describeTiles = XmlUtils.createElement(requestTag, "DescribeTiles");
    Element dtDcpType = XmlUtils.createElement(describeTiles, "DCPType");
    addHttpElement(dtDcpType, requestUrl, version);

    addCapability(requestTag, "GetTile", version, requestStr);
  }

  // not supported at this time.
  // // GetFeatureInfo

  // Exception
  Element exception = XmlUtils.createElement(capability, "Exception");
  XmlUtils.createTextElement2(exception, "Format", "application/vnd.ogc.se_xml");
  XmlUtils.createTextElement2(exception, "Format", "application/vnd.ogc.se_inimage");
  XmlUtils.createTextElement2(exception, "Format", "application/vnd.ogc.se_blank");

  // Layer
  addLayersToCapability(capability, version, pyramidFiles, requestUrl);

  return doc;
}

private void addCapability(Element parent, String capability, Version version, String requestUrl)
{
  // GetMap
  Element element = XmlUtils.createElement(parent, capability);
  String[] formats = ImageHandlerFactory.getMimeFormats(ImageRenderer.class);

  Arrays.sort(formats);
  for (String format : formats)
  {
    XmlUtils.createTextElement2(element, "Format", format);
  }

  Element gmDcpType = XmlUtils.createElement(element, "DCPType");
  addHttpElement(gmDcpType, requestUrl, version);
}

/*
 * Adds data layers to the GetCapabilities response
 */
@SuppressWarnings("squid:S1166") // Exception caught and handled
@SuppressFBWarnings(value = "SIC_INNER_SHOULD_BE_STATIC_ANON", justification = "Just a simple inline comparator")
private void addLayersToCapability(Element capability, Version version, MrsImageDataProvider[] providers, String requestUrl)
{
  Element rootLayer = XmlUtils.createElement(capability, "Layer");
  rootLayer.setAttribute("queryable", "0");
  rootLayer.setAttribute("opaque", "0");
  rootLayer.setAttribute("noSubsets", "0");
  XmlUtils.createTextElement2(rootLayer, "Title", "AllLayers");
  XmlUtils.createTextElement2(rootLayer, "SRS", "EPSG:4326");

  double minx = Double.MAX_VALUE;
  double maxx = -Double.MAX_VALUE;
  double miny = Double.MAX_VALUE;
  double maxy = -Double.MAX_VALUE;

  Arrays.sort(providers, Comparator.comparing(MrsImageDataProvider::getResourceName));

  for (MrsImageDataProvider provider : providers)
  {
    log.debug("pyramids: " + provider.getResourceName());

    Document doc = rootLayer.getOwnerDocument();
    Element layer = doc.createElement("Layer");
    //we'll add the layer to the rootLayer later...
    layer.setAttribute("queryable", "1");
    layer.setAttribute("cascaded", "0");
    layer.setAttribute("opaque", "0");
    layer.setAttribute("noSubsets", "0");
    layer.setAttribute("fixedWidth", "0");
    layer.setAttribute("fixedHeight", "0");
    if (!version.isLess("1.4.0"))
    {
      layer.setAttribute("tiled", "1");
    }

    XmlUtils.createTextElement2(layer, "Title", provider.getResourceName());
    XmlUtils.createTextElement2(layer, "Name", provider.getResourceName());

    try
    {
      MrsPyramidMetadata meta = provider.getMetadataReader().read();

      String abs = meta.getTag("abstract", null);
      if (abs != null)
      {
        XmlUtils.createTextElement2(layer, "Abstract", abs);
      }
    }
    catch (IOException ignored)
    {
    }

    try
    {
      MrsPyramid pyramid = MrsPyramid.open(provider);
      try
      {

        //MrsImagePyramidMetadata pyramidMetadata = pyramid.getMetadata();

        minx = Math.min(minx, pyramid.getBounds().w);
        miny = Math.min(miny, pyramid.getBounds().s);
        maxx = Math.max(maxx, pyramid.getBounds().e);
        maxy = Math.max(maxy, pyramid.getBounds().n);

        if (!version.isLess("1.3.0"))
        {
          double screenPixelSizeInMeters = 0.28 / 1000.0;
          MrsImage image = null;
          try
          {
            image = pyramid.getHighestResImage();

            if (image == null)
            {
              log.warn("Unable to get scale information for layer: " + provider.getResourceName());
            }
            else
            {
              double pixelWidth = image.getMetadata().getPixelWidth(image.getMaxZoomlevel());

              if (!FloatUtils.isEqual(pixelWidth, 0.0))
              {
                //pixel width in meters at the equator.
                double pixelWidthInMeters =
                    LatLng.EARTH_RADIUS * 2 * Math.PI / 360.0 * pixelWidth;
                double minScaleDenominator = pixelWidthInMeters / screenPixelSizeInMeters;
                XmlUtils.createTextElement2(
                    layer, "MinScaleDenominator", Double.toString(minScaleDenominator));
              }
            }

            XmlUtils.createTextElement2(layer, "CRS", "EPSG:4326");
            XmlUtils.createTextElement2(layer, "CRS", "CRS:84");
            Element bb = XmlUtils.createElement(layer, "EX_GeographicBoundingBox");
            XmlUtils.createTextElement2(bb, "westBoundLongitude",
                String.valueOf(pyramid.getBounds().w));
            XmlUtils.createTextElement2(bb, "eastBoundLongitude",
                String.valueOf(pyramid.getBounds().e));
            XmlUtils.createTextElement2(bb, "southBoundLatitude",
                String.valueOf(pyramid.getBounds().s));
            XmlUtils.createTextElement2(bb, "northBoundLatitude",
                String.valueOf(pyramid.getBounds().n));

            bb = XmlUtils.createElement(layer, "BoundingBox");
            XmlUtils.createTextElement2(bb, "CRS", "EPSG:4326");
            XmlUtils.createTextElement2(bb, "minx",
                String.valueOf(pyramid.getBounds().w));
            XmlUtils.createTextElement2(bb, "maxx",
                String.valueOf(pyramid.getBounds().e));
            XmlUtils.createTextElement2(bb, "miny",
                String.valueOf(pyramid.getBounds().s));
            XmlUtils.createTextElement2(bb, "maxy",
                String.valueOf(pyramid.getBounds().n));

          }
          finally
          {
            if (image != null)
            {
              image.close();
            }
          }
        }
        else
        {
          XmlUtils.createTextElement2(layer, "SRS", "EPSG:4326");
        }

        Element bb = XmlUtils.createElement(layer, "LatLonBoundingBox");
        bb.setAttribute("minx", String.valueOf(pyramid.getBounds().w));
        bb.setAttribute("miny", String.valueOf(pyramid.getBounds().s));
        bb.setAttribute("maxx", String.valueOf(pyramid.getBounds().e));
        bb.setAttribute("maxy", String.valueOf(pyramid.getBounds().n));

        bb = XmlUtils.createElement(layer, "BoundingBox");
        XmlUtils.createTextElement2(bb, "SRS", "EPSG:4326");
        XmlUtils.createTextElement2(bb, "minx",
            String.valueOf(pyramid.getBounds().w));
        XmlUtils.createTextElement2(bb, "maxx",
            String.valueOf(pyramid.getBounds().e));
        XmlUtils.createTextElement2(bb, "miny",
            String.valueOf(pyramid.getBounds().s));
        XmlUtils.createTextElement2(bb, "maxy",
            String.valueOf(pyramid.getBounds().n));

        // make styles

        // All layers have the default style
        Element style = XmlUtils.createElement(layer, "Style");
        XmlUtils.createTextElement2(style, "Name", "Default");

        Element legend;
        int bands = pyramid.getMetadata().getBands();
        // Add the colorscale styles if there are only 1 band...
        if (bands == 1)
        {
          ColorScale[] scales = ColorScaleManager.getColorScaleList();

          String csname = pyramid.getMetadata().getTag(MrGeoConstants.MRGEO_DEFAULT_COLORSCALE);
          boolean haveDefault = false;
          if (csname != null)
          {
            try
            {
              ColorScale cs = ColorScaleManager.fromName(csname);
              if (cs != null)
              {
                haveDefault = true;


                String title = cs.getTitle();
                if (title == null)
                {
                  title = cs.getName();
                }

                title = provider.getResourceName() + '-' + title;
                XmlUtils.createTextElement2(style, "Title", title);

                String abst = cs.getDescription();
                if (abst != null)
                {
                  XmlUtils.createTextElement2(style, "Abstract", abst);
                }

                createLegend(requestUrl, provider, "Default", style);
              }
            }
            catch (ColorScale.ColorScaleException ignored)
            {
            }
          }

          if (!haveDefault)
          {
            XmlUtils.createTextElement2(style, "Title", provider.getResourceName() + '-' + "Grayscale");
            XmlUtils.createTextElement2(style, "Abstract",
                "3-band grayscale image, scaled from the min value (0) to max value (255).  All 3 bands contain the same 8-bit value");

            createLegend(requestUrl, provider, "Default", style);
          }

          style = XmlUtils.createElement(layer, "Style");
          XmlUtils.createTextElement2(style, "Name", "GrayScale");
          XmlUtils.createTextElement2(style, "Title", provider.getResourceName() + '-' + "Grayscale");
          XmlUtils.createTextElement2(style, "Abstract",
              "3-band grayscale image, scaled from the min value (0) to max value (255).  All 3 bands contain the same 8-bit value");


          createLegend(requestUrl, provider, "GrayScale", style);

          for (ColorScale scale : scales)
          {
            style = XmlUtils.createElement(layer, "Style");
            XmlUtils.createTextElement2(style, "Name", scale.getName());

            String title = scale.getTitle();
            if (title == null)
            {
              title = scale.getName();
            }

            title = provider.getResourceName() + '-' + title;

            XmlUtils.createTextElement2(style, "Title", title);

            String abst = scale.getDescription();
            if (abst != null)
            {
              XmlUtils.createTextElement2(style, "Abstract", abst);
            }

            createLegend(requestUrl, provider, scale.getName(), style);
          }
        }
        else if (bands >= 3)
        {
          XmlUtils.createTextElement2(style, "Title", "Default RGB");
          XmlUtils.createTextElement2(style, "Abstract",
              "3-band color image,  Colors are taken from bands 1, 2, & 3 respectively");

          style = XmlUtils.createElement(layer, "Style");
          XmlUtils.createTextElement2(style, "Name", "BandR,G,B");
          XmlUtils.createTextElement2(style, "Title", "Band Selection");
        }

        // only add this layer to the XML document if everything else was
        // successful.
        rootLayer.appendChild(layer);
      }
      catch (NullPointerException e)
      {
        log.error("Exception thrown", e);
      }
    }
    catch (IOException e)
    {
      // suck up the exception, there may be a bad file in the images directory...
    }
  }
}

private void createLegend(String requestUrl, MrsImageDataProvider provider, String stylename, Element style)
{
  Element legend;
  legend = XmlUtils.createElement(style, "LegendURL");
  legend.setAttribute("width", "25");
  legend.setAttribute("height", "100");
  XmlUtils.createTextElement2(legend, "Format", "image/jpeg");
  Element onlineResource = XmlUtils.createElement(legend, "OnlineResource");
  onlineResource.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
  onlineResource.setAttribute("xlink:type", "simple");

  UriBuilder builder = UriBuilder.fromUri(requestUrl);
  builder.queryParam("REQUEST", "GetLegendGraphic");
  builder.queryParam("SERVICE", "WMS");
  builder.queryParam("VERSION", "1.0.0");
  builder.queryParam("STYLE", stylename);
  builder.queryParam("LAYER", provider.getResourceName());
  builder.queryParam("FORMAT", "image/jpeg");
  builder.queryParam("WIDTH", 25);
  builder.queryParam("HEIGHT", 100);


  onlineResource.setAttribute("xlink:href", builder.toString());
}
}
