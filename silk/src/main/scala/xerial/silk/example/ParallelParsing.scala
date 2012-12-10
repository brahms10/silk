//--------------------------------------
//
// ParallelParsing.scala
// Since: 2012/12/07 2:39 PM
//
//--------------------------------------

package xerial.silk.example

import xerial.silk.collection.Silk
import xerial.core.io.text.UString

/**
 * @author Taro L. Saito
 */
object ParallelParsing {

  trait ParseResult {
    def isDataLine = false
  }
  case class Header(chr:String, start:Int, step:Int, pos:Long) extends ParseResult {
    def newHeader(offset:Long) = Header(chr, start, step, offset+pos)
  }
  case class DataLine(v:Float) extends ParseResult {
    override def isDataLine = true
  }
  case object BlankLine extends ParseResult

  case class MyDB(header:Silk[Header], value:Silk[DataLine])

  def main(args:Array[String]) {

    def parseLine(count:Int, line:UString) : (Int, ParseResult) = {
      if(line.charAt(0) == '>') {
        (0, Header("", 0, 1, count))
      }
      else {
        val s = line.toString.trim
        if(s.length == 0)
          (count, BlankLine)
        else
          (count+1, DataLine(s.toFloat))
      }
    }

    // read files
    val f = Silk.fromFile("...")

    //  Header or DataLine
    val parsed = for(s <- f.lines.split) yield
      s.scanLeftWith(0){ case (count, line) => parseLine(count, line) }

    // Collect context headers
    val header = parsed collect { case h:Header => h }

    // Fix relative offsets to global offsets
    val correctedHeader = header.scanLeftWith(0L){ case (offset, h) =>
      (offset + h.pos, h.newHeader(offset))
    }
    // Create header table
    val headerTable = correctedHeader sortBy { h => (h.chr, h.start) }
    val binary = parsed collect { case DataLine(v) => v } toArrayBlock map { a => compress(a) }

    // Create DB
    MyDB(headerTable, binary).toSilk.save

  }

}