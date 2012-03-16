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

package xerial.silk.util.mining

import java.util.Random
import xerial.silk.util.Logging
import collection.{GenTraversable, GenTraversableOnce, GenSeq}
import collection.parallel.immutable.ParSeq

//--------------------------------------
//
// KMeans.scala
// Since: 2012/03/15 22:15
//
//--------------------------------------

/**
 * Distance definition of data points for K-Means clustering
 *
 * @author leo
 *
 */
trait PointDistance[T] {

  type PointVector = Array[Double]

  /**
   * Get point vector of the element
   * @param e
   * @return
   */
  def getVector(e: T): PointVector

  /**
   * Return the distance between the given two points
   *
   * @param a
   * @param b
   * @return |a-b|
   */
  def distance(a: PointVector, b: PointVector): Double = {
    val m = a.size
    val sum = (0 until m).par.map(col => Math.pow(a(col) - b(col), 2)).sum
    Math.sqrt(sum)
  }

  /**
   * Return the center of mass of the inputs
   *
   * @param points
   *            list of input points
   * @return |a+b+c+... | / N
   */
  def centerOfMass(points: GenTraversableOnce[PointVector]): PointVector = {
    var n = 0
    val v = points.reduce((a, b) => {
      n += 1;
      a.zip(b).map(x => x._1 + x._2)
    })
    v.map(x => x / n)
  }

  def squareError(points: GenTraversableOnce[PointVector], centor: PointVector): Double = {
    points.reduce((a, b) => a.zip(b).map(x => Math.pow(x._1 - x._2, 2))).sum
  }

  /**
   * Compute the lower bound of the points
   *
   * @param points
   * @return
   */
  def lowerBound(points: GenTraversableOnce[PointVector]): PointVector = {
    points.reduce((a, b) => a.zip(b).map(x => Math.min(x._1, x._2)))
  }

  /**
   * Compute the upper bound of the points
   *
   * @param points
   * @return
   */
  def upperBound(points: GenTraversableOnce[PointVector]): PointVector = {
    points.reduce((a, b) => a.zip(b).map(x => Math.max(x._1, x._2)))
  }
  /**
   * Move the points to the specified direction to the amount of the given distance
   *
   * @param point
   * @return
   */
  def move(point: PointVector, direction: PointVector): PointVector = {
    point.zip(direction).map(x => x._1 + x._2)
  }
  /**
   * The size of dimension;
   *
   * @return
   */
  def dimSize: Int
}

object KMeans {

  class Config {
    var maxIteration: Int = 300
  }

  /**
   * Holds K-means clustering result
   *
   * @author leo
   *
   */
  case class ClusterSet[T](K: Int, points: Array[T], metric: PointDistance[T], centroids: GenSeq[Array[Double]], clusterAssignment: Array[Int]) {

    lazy val averageOfDistance: Double = {
      // TODO map(point -> distance) => reduce(seq[distance] -> sum(distance)
      val dist = clusterAssignment.par.zipWithIndex.map {
        case (clusterID, pointIndex) =>
          val centroid = centroids(clusterID)
          val point = points(pointIndex)
          Math.pow(metric.distance(centroid, metric.getVector(point)), 2)
      }
      val distSum = dist.par.reduce((a, b) => a + b)
      distSum / points.length
    }

    /**
     * Average of squared distance of each point to its belonging centroids
     */
    //var averageOfDistance: Double = Double.MAX_VALUE
    /**
     * List of centroids
     */
    //var centroid: IndexedSet[T] = new IndexedSet[T]
    /**
     * Array of cluster IDs of the point p_0, ..., p_{N-1};
     */
  }

}

/**
 * K-means clustering
 *
 * @author leo
 *
 */
class KMeans[T](config: KMeans.Config, metric: PointDistance[T]) extends Logging {

  import KMeans._

  type PointVector = Array[Double]

  private val random: Random = new Random(0)


  private def hasDuplicate(points: GenSeq[PointVector]): Boolean = {
    val uniquePoints = points.distinct
    points.length != uniquePoints.length
  }

  /**
   * Randomly choose K-centroids from the input data set
   *
   * @param K
   * @param points
   * @return
   */
  protected def initCentroids(K: Int, points: Array[T]): GenSeq[PointVector] = {
    val N: Int = points.size

    if (K > N)
      throw new IllegalArgumentException("K(%d) must be smaller than N(%d)".format(K, N))

    val uniquePoints = points.par.map(metric.getVector(_)).distinct
    val UN = uniquePoints.length

    def pickCentroid(centroids: List[PointVector], remaining: Int): List[PointVector] = {
      if (remaining == 0)
        centroids
      val r = random.nextInt(UN)
      val v = uniquePoints(r)
      pickCentroid(v :: centroids, remaining - 1)
    }

    pickCentroid(List(), K).toSeq
  }

  /**
   * @param K
   *            number of clusters
   * @param points
   *            input data points in the matrix format
   * @throws Exception
   */
  def execute(K: Int, points: Array[T]): ClusterSet[T] = {
    execute(K, points, initCentroids(K, points))
  }
  /**
   * @param K
   *            number of clusters
   * @param points
   *            input data points in the matrix format
   * @param centroids
   *            initial centroids
   * @throws Exception
   */
  def execute(K: Int, points: Array[T], centroids: GenSeq[PointVector]): ClusterSet[T] = {
    val N: Int = points.length
    //var prevClusters: KMeans.ClusterSet[T] = new KMeans.ClusterSet[T](K, N, centroids)

    val maxIteration: Int = config.maxIteration
    def iteration(i: Int, cluster: ClusterSet[T]): ClusterSet[T] = {
      debug("iteration: %d", i + 1)
      if (i >= maxIteration)
        cluster
      else {
        val newCluster = EStep(K, points, centroids)
        if (newCluster.averageOfDistance >= cluster.averageOfDistance) {
          cluster
        }
        else {
          val centroids = MStep(K, points, newCluster)
          if (hasDuplicate(centroids))
            cluster
          else
            iteration(i + 1, newCluster)
        }
      }
    }

    iteration(0, new ClusterSet[T](K, points, metric, centroids, Array.empty))
  }

  /**
   * Assign each point to the closest centroid
   * @param K
   * @param points
   * @param centroids
   * @return
   */
  protected def EStep(K: Int, points: Array[T], centroids: GenSeq[PointVector]): ClusterSet[T] = {
    if (K != centroids.length)
      throw new IllegalStateException("K=%d, but # of centrods is %d".format(K, centroids.length))

    def findClosestCentroid(p: T): Int = {
      val dist = (0 until centroids.length).map {
        cid => (cid, metric.distance(p, centroids(cid)))
      }
      val min = dist.minBy {
        case (cid, d) => d
      }
      min._1
    }
    val N = points.length
    val clusterAssignment: Array[Int] = Array.fill(N)(-1)
    (0 until N).par.foreach {
      i =>
        clusterAssignment(i) = findClosestCentroid(points(i))
    }

    ClusterSet[T](K, points, metric, centroids, clusterAssignment)
  }

  /**
   * Returns the list of new centroids by taking the center of mass of the points in the clusters
   *
   * @param K
   * @param points
   * @param cluster
   * @return
   */
  protected def MStep(K: Int, points: Array[T], cluster: ClusterSet[T]): GenSeq[PointVector] = {
    val newCentroids = (0 until K).par.map {
      c =>
        val pointsInCluster = for ((cid, index) <- cluster.clusterAssignment.zipWithIndex; if (c == cid)) yield {
          metric.getVector(points(index))
        }
        metric.centerOfMass(pointsInCluster)
    }
    newCentroids
  }

}
