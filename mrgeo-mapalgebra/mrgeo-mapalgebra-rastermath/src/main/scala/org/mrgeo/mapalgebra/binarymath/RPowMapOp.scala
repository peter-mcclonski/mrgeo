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

package org.mrgeo.mapalgebra.binarymath

import org.mrgeo.mapalgebra.parser.ParserNode
import org.mrgeo.mapalgebra.raster.RasterMapOp
import org.mrgeo.mapalgebra.{MapOp, MapOpRegistrar}

object RPowMapOp extends MapOpRegistrar {
  override def register:Array[String] = {
    Array[String]("rpow")
  }

  def create(const:Double, raster:RasterMapOp):MapOp = {
    new PowMapOp(Some(raster), Some(const), true)
  }

  def create(rasterA:RasterMapOp, rasterB:RasterMapOp):MapOp = {
    new PowMapOp(Some(rasterA), Some(rasterB))
  }

  override def apply(node:ParserNode, variables:String => Option[ParserNode]):MapOp =
    new PowMapOp(node, variables)
}

class RPowMapOp extends PowMapOp {}
