//--------------------------------------
//
// ClusterTest.scala
// Since: 2013/04/10 22:52
//
//--------------------------------------

package xerial.silk.multijvm

import xerial.silk.util.SilkSpec
import xerial.larray.{MMapMode, LArray}
import java.io.File
import xerial.core.io.IOUtil
import xerial.silk.util.ThreadUtil.ThreadManager
import xerial.silk.core.Silk
import xerial.silk.cluster.SilkClient.{SilkClientRef, Terminate}
import xerial.silk.cluster.ProcessBarrier
import xerial.silk.cluster._
import org.apache.zookeeper.{WatchedEvent, Watcher}
import org.apache.zookeeper.Watcher.Event.EventType
import com.netflix.curator.framework.recipes.barriers.DistributedDoubleBarrier

/**
 * Base trait for testing with 4-cluster nodes
 */
trait Cluster4Spec extends ClusterSpec {
  def numProcesses = 4
}

/**
 * Base trait for testing with 3-cluster nodes
 */
trait Cluster3Spec extends ClusterSpec {
  def numProcesses = 3
}
/**
 * Base trait for testing with 2-cluster nodes
 */
trait Cluster2Spec extends ClusterSpec {
  def numProcesses = 2
}

trait ClusterSpec extends SilkSpec with ProcessBarrier {

  def processID = {
    val n = getClass.getSimpleName
    val p = "[0-9]".r
    val id = p.findAllIn(n).toSeq.last.toInt
    id
  }

  before {
    xerial.silk.configureLog4j
    if (processID == 1) {
      cleanup
    }
    else
      Thread.sleep(1000)
  }



  def writeZkClientPort {
    if (processID == 1) {
      info(s"Write zkClientPort: ${config.zk.clientPort}")
      val m = LArray.mmap(new File("target/zkPort"), 0, 4, MMapMode.READ_WRITE)
      m.putInt(0, config.zk.clientPort)
      m.flush
      m.close
    }
  }

  def getZkConnectAddress = {
    val m = LArray.mmap(new File("target/zkPort"), 0, 4, MMapMode.READ_ONLY)
    val addr = s"127.0.0.1:${m.getInt(0)}"
    m.close
    addr
  }

  def enterCuratorBarrier(zk: ZooKeeperClient, barrierPath: String, nodeName: String)
  {
    info(s"entering barrier: ${nodeName}")
    val ddb = new DistributedDoubleBarrier(zk.curatorFramework, barrierPath + nodeName, numProcesses)
    ddb.enter()
    info(s"exit barrier: ${nodeName}")
  }

  def start[U](f: SilkClientRef => U) {
    try {
      val barrierNodePath = s"/target/lock/${this.getClass.getSimpleName.replaceAll("[0-9]+", "")}/"
      if (processID == 1) {
        StandaloneCluster.withCluster {
          writeZkClientPort
          for (zk <- ZooKeeper.zkClient(getZkConnectAddress))
          {
            enterCuratorBarrier(zk, barrierNodePath, "zkIsReady")
            SilkClient.startClient(Host(s"jvm${processID}", "127.0.0.1"), getZkConnectAddress) {
              client =>
                enterCuratorBarrier(zk, barrierNodePath, "clientIsReady")
                try
                  f(client)
                finally
                  enterCuratorBarrier(zk, barrierNodePath, "clientBeforeFinished")
            }
            enterCuratorBarrier(zk, barrierNodePath, "clientTerminated")
          }
        }
      }
      else {
        for (zk <- ZooKeeper.zkClient(getZkConnectAddress))
        {
          enterCuratorBarrier(zk, barrierNodePath, "zkIsReady")
          withConfig(Config(silkClientPort = IOUtil.randomPort, dataServerPort = IOUtil.randomPort)) {

            SilkClient.startClient(Host(s"jvm${processID}", "127.0.0.1"), getZkConnectAddress) {
              client =>
                enterCuratorBarrier(zk, barrierNodePath, "clientIsReady")
                try
                  f(client)
                finally
                  enterCuratorBarrier(zk, barrierNodePath, "clientBeforeFinished")
            }
            enterCuratorBarrier(zk, barrierNodePath, "clientTerminated")
          }
        }
      }
    }
    finally {
      enterBarrier("terminate")
    }

  }
}


class ClusterTestMultiJvm1 extends Cluster3Spec {

  "start cluster" in {
    start { client =>
      val nodeList = Silk.hosts
      info(s"nodes: ${nodeList.mkString(", ")}")

      // do something here
      Thread.sleep(1000)
    }
  }

}

class ClusterTestMultiJvm2 extends Cluster3Spec {
  "start cluster" in {
    start { client => }
  }

}

class ClusterTestMultiJvm3 extends Cluster3Spec {
  "start cluster" in {
    start { client => }
  }

}