/*
 * Copyright 2012 Taro L. Saito
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xerial.silk.glens

//--------------------------------------
//
// BED.scala
// Since: 2012/03/16 14:02
//
//--------------------------------------

/**
 * UCSC's BED format
 * @param chr
 * @param start
 * @param end
 */
class BED(val chr: String, val start: Int, val end: Int, val strand: Strand)
  extends GenomicInterval[BED] {
  def newRange(newStart: Int, newEnd: Int) = new BED(chr, newStart, newEnd, strand)

}

/**
 * BED full entry. Original BED file is zero-origin, but we use one-origin for compatibility with the other formats
 *
 * @author leo
 */
class BEDGene
(
  chr: String,
  start: Int,
  end: Int,
  strand: Strand,
  val name: String,
  val score: Int,
  val thickStart: Int,
  val thickEnd: Int,
  val itemRgb: String,
  val blockCount: Int,
  val blockSizes: Array[Int],
  val blockStarts: Array[Int]
  )
  extends BED(chr, start, end, strand) {

  override def toString = "%s %s[%s,%s)".format(name, chr, start, end)

  private def concatenate(blocks:Array[Int]) :String = {
    val b = new StringBuilder
    blocks.foreach{e => b.append(e.toString); b.append(",")}
    b.toString
  } 
  
  def toBEDLine : String = (chr, start-1, end-1, name, score, strand, thickStart-1, thickEnd-1, if(itemRgb != null) itemRgb else "0", blockCount, concatenate(blockSizes), concatenate(blockStarts)).productIterator.mkString("\t")

  def cdsStart = cdsRange.start
  def cdsEnd = cdsRange.end
  lazy val cdsRange: GInterval = new GInterval(chr, thickStart, thickEnd, strand)
  lazy val exons: Array[GInterval] = {
    for ((size, exonStart) <- blockSizes.zip(blockStarts)) yield {
      new GInterval(chr, start, end, strand)
    }
  }
  lazy val cds: Array[GInterval] = {
    for (ex <- exons; c <- ex.intersection(cdsRange)) yield {
      c
    }
  }

  def firstExon: Option[GInterval] = {
    strand match {
      case Forward => exons.headOption
      case Reverse => exons.lastOption
    }
  }

  def lastExon: Option[GInterval] = {
    strand match {
      case Forward => exons.lastOption
      case Reverse => exons.headOption
    }
  }

}

object BED {

}
