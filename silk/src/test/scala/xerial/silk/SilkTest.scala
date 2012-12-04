//--------------------------------------
//
// SilkTest.scala
// Since: 2012/12/03 10:40 AM
//
//--------------------------------------

package xerial.silk

import util.SilkSpec

/**
 * @author Taro L. Saito
 */
class SilkTest extends SilkSpec {

  "InMemorySilk" should {

    "support basic Seq operations" in {
      val s = InMemorySilk(Seq(0, 1, 2, 3, 4, 5, 6))
      val s2 = s.map(x => x*2)
      debug(s2.mkString(", "))
      s2.getClass should be (classOf[InMemorySilk[_]])
    }

    "support for loop" in {
      val s = InMemorySilk(Seq(0, 1, 2, 5, 34))
      val s2 = for(x <- s.withFilter(_ < 5)) yield {
        x * 10
      }
      debug(s2.mkString(", "))
    }

  }
}