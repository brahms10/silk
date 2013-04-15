

package xerial.silk.cluster

import org.scalatest._
import xerial.silk.util.SilkSpec
import xerial.larray.{MMapMode, LArray}
import java.io.File
import xerial.core.io.IOUtil
import xerial.silk.cluster.ZooKeeper._
import scala.Some
import xerial.silk.cluster.SilkClient.ClientInfo
import xerial.silk.core.Silk
import xerial.silk.cluster
import xerial.silk.util.ThreadUtil.ThreadManager




class ScatterTestMultiJvm1 extends Cluster2Spec {


  "scatter" should {

    "distribute data" in {
      val l = LArray.of[Int](10)
      import xerial.larray._
      import xerial.silk._

      start { client =>

        // data on memory
        val sharedMemoryFile = new File(cluster.config.silkTmpDir, "sample.dat")
        val sharedMemory = LArray.mmap(sharedMemoryFile, 0, l.byteLength, MMapMode.READ_WRITE)
        l.copyTo(0, sharedMemory, 0, l.byteLength)

        // Register the data to the local DataServer
        client ! SilkClient.RegisterData(sharedMemoryFile)

        // Send file location to JVM2
        var index = 0L
        val blockSize = math.ceil(l.byteLength / 2.toDouble).toLong
        for(n <- Silk.hosts; remote <- SilkClient.remoteClient(n.host, n.port)) {
          val offset = blockSize * index
          val size = math.min(blockSize, l.byteLength - offset)
          remote ! SilkClient.DownloadDataFrom(StandaloneCluster.lh, n.dataServerPort, sharedMemoryFile, offset, size)
          index += 1
        }

        Thread.sleep(3000)
      }
    }
  }
}

class ScatterTestMultiJvm2 extends Cluster2Spec {

  xerial.silk.configureLog4j

  "scatter" should {
    "distribute data" in {
      start { client =>


      }
    }
  }

}