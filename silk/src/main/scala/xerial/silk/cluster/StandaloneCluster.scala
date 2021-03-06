/*
 * Copyright 2013 Taro L. Saito
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

//--------------------------------------
//
// StandaloneCluster.scala
// Since: 2012/12/29 22:33
//
//--------------------------------------

package xerial.silk.cluster

import java.io.File
import xerial.core.io.Path._
import xerial.silk.cluster.ZooKeeper.{ZkStandalone, ZkQuorumPeer}
import xerial.silk.util.ThreadUtil
import xerial.core.log.Logger
import xerial.silk.cluster.SilkClient.{Register, Terminate, ClientInfo}
import xerial.core.util.Shell
import xerial.silk.cluster._
import com.netflix.curator.test.{InstanceSpec, TestingServer, TestingZooKeeperServer}
import xerial.core.io.IOUtil
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await
import java.util.concurrent.TimeoutException


object StandaloneCluster {

  val lh = Host("localhost", "127.0.0.1")

  def withCluster(f: => Unit) {
    val tmpDir : File = IOUtil.createTempDir(new File("target"), "silk-tmp").getAbsoluteFile
    var cluster : Option[StandaloneCluster] = None
    try {
      withConfig(Config(silkHome=tmpDir, zk=ZkConfig(zkServers = Some(Seq(new ZkEnsembleHost(lh)))))) {
        cluster = Some(new StandaloneCluster)
        cluster map (_.start)
        f
      }
    }
    finally {
      cluster.map(_.stop)
      //SilkClient.closeActorSystem
      tmpDir.rmdirs
    }
  }


}


/**
 * Emulates the cluster environment in a single machine
 *
 * @author Taro L. Saito
 */
class StandaloneCluster extends Logger {

  xerial.silk.suppressLog4jwarning

  private val t = ThreadUtil.newManager(1)
  private var zkServer : Option[TestingServer] = None

  import StandaloneCluster._

  def start {
    // Startup a single zookeeper
    info("Running a zookeeper server. zkDir:%s", config.zkDir)
    //val quorumConfig = ZooKeeper.buildQuorumConfig(0, config.zk.getZkServers)
    zkServer = Some(new TestingServer(new InstanceSpec(config.zkDir, config.zk.clientPort, config.zk.quorumPort, config.zk.leaderElectionPort, false, 0)))

    t.submit {
      SilkClient.startClient(lh, config.zk.zkServersConnectString)
    }

    // Wait until SilkClient is started
    for(client <- SilkClient.remoteClient(lh)) {
      var isRunning = false
      var count = 0
      val maxAwait = 5
      while(!isRunning && count < maxAwait) {
        try {
          debug("Waiting responses from SilkClient")
          val r = client ? SilkClient.Status
          isRunning = true
        }
        catch {
          case e: TimeoutException => count += 1
        }
      }
      if(count >= maxAwait) {
        throw new IllegalStateException("Failed to find SilkClient")
      }

    }
  }

  // Access to the zookeeper, then retrieve a SilkClient list (hostname and client port)

  /**
   * Terminate the standalone cluster
   */
  def stop {
    info("Sending a stop signal to the client")
    for(cli <- SilkClient.remoteClient(lh)) {
      cli ! Terminate
    }
    t.join
    info("Shutting down the zookeeper server")
    zkServer.map(_.stop)
  }


}