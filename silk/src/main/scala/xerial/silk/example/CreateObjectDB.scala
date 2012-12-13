//--------------------------------------
//
// CreateObjectDB.scala
// Since: 2012/12/07 6:40 PM
//
//--------------------------------------

package xerial.silk.example

import util.Random
import xerial.silk._
import xerial.silk.collection.Silk

/**
 * @author Taro L. Saito
 */
object CreateObjectDB {

  case class Person(id:Int, name:String)

  def main(args:Array[String]) {

    val N = 100000
    val persons = for(i <- 0 until N) yield Person(i, Random.nextString(2 + Random.nextInt(10)))

    val p : Silk[Person] = persons.toSilk

    // create db, compress and save
    val db = p.save

    // TODO: How do we load the Silk data from the storage?


  }

}