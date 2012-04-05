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

package xerial.silk.util

import collection.mutable.{HashSet, ArrayBuffer}


//--------------------------------------
//
// UnionFind.scala
// Since: 2012/04/05 15:03
//
//--------------------------------------

/**
 * Union-find based disjoint set implementation
 *
 * @author leo
 *
 */
class DisjointSet[E] extends collection.mutable.Set[E] {

  private val elem = new ArrayBuffer[Container]
  private val elemToID = collection.mutable.Map[E, Int]()
  private val idToElem = collection.mutable.Map[Int, E]()
  private var counter = 0
  
  class Container(val elem:E, var parentID:Int, var rank:Int)

  private def issueNewID(e: E) = {
    val newID = counter
    elem += new Container(e, newID, 0)
    counter += 1
    idToElem += newID -> e
    newID
  }

  def id(e: E) : Int = {
    elemToID.getOrElseUpdate(e, issueNewID(e))
  }

  /**
   * @param e
   */
  def +=(e: E) : this.type = {
    id(e)  // issue new id for the element
    this
  }
  
  def -=(e:E) : this.type = {
    throw new UnsupportedOperationException("removal")
  }
  def contains(e:E) = {
    elemToID.contains(e)
  }

  def iterator = (for(e <- elem) yield e.elem).toIterator

  def groups : Iterable[Seq[E]] = {
    for(r <- representativeElements) yield {
      elementsInTheSameClass(r).toSeq
    }
  }

  /**
   * Find the disjoint set ID of the given element
   *
   * @param e
   *            element
   * @return
   */
  def findClassID(e: E) = classID(id(e))

  private def classID(id:Int) : Int = {
    val parentID = elem(id).parentID
    if (id != parentID) {
      // path compression
      elem(id).parentID = classID(parentID)
    }
    elem(id).parentID
  }
  
  def representativeElements: Iterable[E] = {
    for ((id, elem) <- idToElem if id == classID(id))
    yield elem
  }

  def elementsInTheSameClass(e: E) = {
    val cid = findClassID(e)
    for (i <- 0 until elem.length if classID(i) == cid) yield {
      elem(i).elem
    }
  }

  def union(x: E, y: E) {
    linkByID(findClassID(x), findClassID(y))
  }

  def link(x: E, y: E) {
    val xID = id(x)
    val yID = id(y)
    linkByID(xID, yID);
  }

  private def linkByID (xID:Int, yID:Int) {
    if (elem(xID).rank > elem(yID).rank) {
      elem(yID).parentID = xID
    }
    else {
      elem(xID).parentID = yID
      if (elem(xID).rank == elem(yID).rank)
        elem(yID).rank = elem(yID).rank + 1
    }
  }

  override def size = {
    elem.size
  }


}