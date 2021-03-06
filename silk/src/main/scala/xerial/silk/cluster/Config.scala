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
// Config.scala
// Since: 2013/01/07 11:18 AM
//
//--------------------------------------

package xerial.silk.cluster

import java.io.File
import xerial.core.io.Path._
import xerial.core.log.Logger
import ZooKeeper._

object Config extends Logger {
  private[cluster] def defaultSilkHome : File = {
    sys.props.get("silk.home") map { new File(_) } getOrElse {
      val homeDir = sys.props.get("user.home") getOrElse ("")
      new File(homeDir, ".silk")
    }
  }

  /**
   * Get the default zookeeper servers
   * @return
   */
  private[cluster] lazy val defaultZKServers: Seq[ZkEnsembleHost] = {

    // read zkServer lists from $HOME/.silk/zkhosts file
    val ensembleServers: Seq[ZkEnsembleHost] = readHostsFile(config.zkHosts) getOrElse {
      debug("Selecting candidates of zookeeper servers from %s", config.silkHosts)
      val zkHosts = for(candidates <- readHostsFile(config.silkHosts) if candidates.length > 0) yield {
        if(candidates.length >= 3)
          Seq() ++ candidates.take(3) // use first three hosts as zk servers
        else {
          warn("Not enough servers found in %s file (required more than 3 servers for the reliability). Start with a single zookeeper server", config.silkHosts)
          candidates.take(1)
        }
      }

      zkHosts.getOrElse {
        warn("Use localhost as a single zookeeper server")
        Seq(new ZkEnsembleHost(localhost))
      }
    }

    debug("Selected zookeeper servers: %s", ensembleServers.mkString(","))
    ensembleServers
  }

}


/**
 * Cluster configuration
 * @author Taro L. Saito
 */
case class Config(silkHome : File = Config.defaultSilkHome,
                  silkMasterPort: Int = 8983,
                  silkClientPort: Int = 8984,
                  dataServerPort: Int = 8985,
                  zk: ZkConfig = ZkConfig()) {
  val silkHosts : File = silkHome / "hosts"
  val zkHosts : File = silkHome / "zkhosts"
  val silkConfig : File = silkHome / "config.silk"
  val silkLocalDir : File = silkHome / "local"
  val silkTmpDir : File = silkLocalDir / "tmp"
  val silkLogDir : File = silkLocalDir / "log"
  val zkDir : File = silkLocalDir / "zk"

  for(d <- Seq(silkLocalDir, silkTmpDir, silkLogDir, zkDir) if !d.exists) d.mkdirs

  def zkServerDir(id:Int) : File = new File(zkDir, "server.%d".format(id))
  def zkMyIDFile(id:Int) : File = new File(zkServerDir(id), "myid")
}


/**
 * Zookeeper configuration
 * @param basePath
 * @param quorumPort
 * @param leaderElectionPort
 * @param clientPort
 * @param tickTime
 * @param initLimit
 * @param syncLimit
 * @param zkServers comma separated string of (zookeeper address):(quorumPort):(leaderElectionPort)
 */
case class ZkConfig(basePath: ZkPath = ZkPath("/silk"),
                    clientPort: Int = 8980,
                    quorumPort: Int = 8981,
                    leaderElectionPort: Int = 8982,
                    tickTime: Int = 2000,
                    initLimit: Int = 10,
                    syncLimit: Int = 5,
                    clientConnectionMaxRetry : Int = 10,
                    clientConnectionTickTime : Int = 500,
                    clientSessionTimeout : Int = 60 * 1000,
                    clientConnectionTimeout : Int = 3 * 1000,
                    private val zkServers : Option[Seq[ZkEnsembleHost]] = None) {
  val statusPath = basePath / "zkstatus"
  val clusterPath = basePath / "cluster"
  val clusterNodePath = clusterPath / "node"
  val leaderElectionPath = clusterPath / "le"

  def clientEntryPath(hostName:String) : ZkPath = clusterNodePath / hostName

  def getZkServers = zkServers getOrElse Config.defaultZKServers

  def zkServersConnectString = getZkServers.map(_.connectAddress).mkString(",")
}


