//--------------------------------------
//
// SilkLike.scala
// Since: 2012/12/04 4:24 PM
//
//--------------------------------------

package xerial.silk.collection

import collection.{GenTraversableOnce, mutable, GenTraversable}
import util.Random

/**
 * A basic implementation of [[xerial.silk.collection.GenSilk]]
 * @tparam A
 */
trait SilkLike[+A] extends SilkOps[A] {

  def isEmpty = iterator.isEmpty

  def aggregate[B](z:B)(seqop:(B, A) => B, combop:(B, B)=>B):B = foldLeft(z)(seqop)

  def reduce[A1 >: A](op: (A1, A1) => A1): A1 = reduceLeft(op)

  def reduceLeft[B >: A](op: (B, A) => B): B = {
    var first = true
    var acc: B = 0.asInstanceOf[B]

    for (x <- iterator) {
      if (first) {
        acc = x
        first = false
      }
      else acc = op(acc, x)
    }
    acc
  }

  def foldLeft[B](z:B)(op:(B, A) => B): B = {
    var result = z
    foreach (x => result = op(result, x))
    result
  }

  def fold[A1 >: A](z:A1)(op: (A1, A1) => A1): A1 = foldLeft(z)(op)


  def sum[B >: A](implicit num: Numeric[B]): B = foldLeft(num.zero)(num.plus)

  def product[B >: A](implicit num: Numeric[B]): B = foldLeft(num.one)(num.times)

  def min[B >: A](implicit cmp: Ordering[B]): A = {
    if (isEmpty)
      throw new UnsupportedOperationException("empty.min")
    reduceLeft((x, y) => if (cmp.lteq(x, y)) x else y)
  }

  def max[B >: A](implicit cmp: Ordering[B]): A = {
    if (isEmpty)
      throw new UnsupportedOperationException("empty.max")

    reduceLeft((x, y) => if (cmp.gteq(x, y)) x else y)
  }

  def maxBy[B](f: A => B)(implicit cmp: Ordering[B]): A = {
    if (isEmpty)
      throw new UnsupportedOperationException("empty.maxBy")

    reduceLeft((x, y) => if (cmp.gteq(f(x), f(y))) x else y)
  }
  def minBy[B](f: A => B)(implicit cmp: Ordering[B]): A = {
    if (isEmpty)
      throw new UnsupportedOperationException("empty.minBy")

    reduceLeft((x, y) => if (cmp.lteq(f(x), f(y))) x else y)
  }


  def mkString(start: String, sep: String, end: String): String =
    addString(new StringBuilder(), start, sep, end).toString

  def addString(b: StringBuilder, start: String, sep: String, end: String): StringBuilder = {
    var first = true

    b append start
    for (x <- iterator) {
      if (first) {
        b append x
        first = false
      }
      else {
        b append sep
        b append x
      }
    }
    b append end

    b
  }


  def size = {
    var count = 0
    for(x <- this.iterator)
      count += 1
    count
  }


  def foreach[U](f: A => U) {
    for(x <- this.iterator)
      f(x)
  }

  def map[B](f: A => B) : Silk[B] = {
    val b = newBuilder[B]
    for(x <- this.iterator)
      b += f(x)
    b.result
  }

  def flatMap[B](f: A => GenTraversableOnce[B]) : Silk[B] = {
    val b = newBuilder[B]
    for(x <- this.iterator; s <- f(x)) {
      b += s
    }
    b.result
  }

  def filter(p: A => Boolean) : Silk[A] = {
    val b = newBuilder[A]
    for(x <- this.iterator if p(x))
      b += x
    b.result
  }


  def collect[B](pf:PartialFunction[A, B]) : Silk[B] = {
    val b = newBuilder[B]
    for(x <- this.iterator; if(pf.isDefinedAt(x))) b += pf(x)
    b.result
  }

  def collectFirst[B](pf: PartialFunction[A, B]): Option[B] = {
    for (x <- iterator) { // make sure to use an iterator or `seq`
      if (pf isDefinedAt x)
        return Some(pf(x))
    }
    None
  }


  def isSingle = size == 1

  def project[B](implicit mapping:ObjectMapping[A, B]) : Silk[B] = {
    val b = newBuilder[B]
    for(e <- this) b += mapping(e)
    b.result
  }

  def length: Int = size


  def groupBy[K](f: A => K): Silk[(K, Silk[A])] = {
    val m = collection.mutable.Map.empty[K, mutable.Builder[A, Silk[A]]]
    for(elem <- iterator) {
      val key = f(elem)
      val b = m.getOrElseUpdate(key, newBuilder[A])
      b += elem
    }
    val r = newBuilder[(K, Silk[A])]
    for((k, b) <- m) {
      r += k -> b.result
    }
    r.result
  }

  def join[K, B](other:Silk[B], k1: A => K, k2: B => K): Silk[(K, Silk[(A, B)])] = {

    def createMap[T](lst:SilkOps[T], f: T => K): collection.mutable.Map[K, mutable.Builder[T, Seq[T]]] = {
      val m = collection.mutable.Map.empty[K, mutable.Builder[T, Seq[T]]]
      for(elem <- lst) {
        val key = f(elem)
        val b = m.getOrElseUpdate(key, Seq.newBuilder[T])
        b += elem
      }
      m
    }

    val a = createMap(this, k1)
    val b = createMap(other, k2)

    val m = newBuilder[(K, Silk[(A, B)])]
    for(k <- a.keys) yield {
      val pairs = newBuilder[(A, B)]
      for(ae <- a(k).result; be <-b(k).result) {
        pairs += ((ae, be))
      }
      m += k -> pairs.result()
    }
    m.result
  }

  def joinBy[B](other:Silk[B], cond: (A, B) => Boolean) : Silk[(A, B)] = {
    val m = newBuilder[(A, B)]
    for(a <- this; b <- other) {
      if(cond(a, b))
        m += ((a, b))
    }
    m.result
  }

  def sortBy[K](keyExtractor: A => K)(implicit ord:Ordering[K])  = sorted(ord on keyExtractor)
  def sorted[A1 >: A](implicit ord: Ordering[A1]) : Silk[A1] = {
    val len = this.length
    val arr = toArraySeq
    java.util.Arrays.sort(arr.array, ord.asInstanceOf[Ordering[Object]])
    val b = newBuilder[A1]
    b.sizeHint(len)
    for (x <- arr) b += x
    b.result
  }

  private def toArraySeq[A1 >: A] : mutable.ArraySeq[A1] = {
    val arr = new mutable.ArraySeq[A1](this.length)
    var i = 0
    for (x <- this) {
      arr(i) = x
      i += 1
    }
    arr
  }

  def takeSample(proportion:Double) : Silk[A] = {
    val arr = toArraySeq
    val N = arr.length
    val num = (N * proportion + 0.5).toInt
    var i = 0
    val b = newBuilder[A]
    (0 until num).foreach { i =>
      b += arr(Random.nextInt(N))
    }
    b.result
  }


  def withFilter(p: A => Boolean) = new WithFilter(p)

  class WithFilter(p: A => Boolean) extends SilkMonadicFilter[A] {

    def map[B](f: (A) => B) : Silk[B] = {
      val b = newBuilder[B]
      for(x <- iterator)
        if(p(x)) b += f(x)
      b.result
    }

    def flatMap[B](f: (A) => GenTraversableOnce[B]) : Silk[B] = {
      val b = newBuilder[B]
      for(x <- iterator; e <- f(x))
        if(p(x)) b += e
      b.result
    }

    def foreach[U](f: (A) => U) {
      for(x <- iterator)
        if(p(x)) f(x)
    }

    def withFilter(q: (A) => Boolean) : WithFilter = new WithFilter(x => p(x) && q(x))
  }


}

