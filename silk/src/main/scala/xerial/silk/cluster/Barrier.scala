//--------------------------------------
//
// Barrier.scala
// Since: 2013/04/10 4:20 PM
//
//--------------------------------------

package xerial.silk.cluster

import java.util.concurrent.CyclicBarrier
import xerial.core.log.Logger
import java.io.{FileFilter, File}
import xerial.larray.{LArray, MMapMode, MappedLByteArray}

/**
 * @author Taro L. Saito
 */
class Barrier(numThreads:Int) extends Logger {

  import scala.collection.JavaConversions._
  val barrier = new java.util.concurrent.ConcurrentHashMap[String, CyclicBarrier]()

  def enter(name:String) {
    val b : CyclicBarrier = synchronized {
      barrier.getOrElseUpdate(name, new CyclicBarrier(numThreads))
    }
    debug(f"[Thread-${Thread.currentThread.getId}] entering barrier: $name")
    b.await
  }

}

trait ProcessBarrier extends Logger {
  def numProcesses:Int
  def processID:Int
  def lockFolder:File = new File("target/lock")

  def cleanup = {
    val lockFile = lockFolder.listFiles(new FileFilter {
      def accept(pathname: File) = pathname.getName.endsWith(".barrier")
    })
    if(lockFile != null)
      lockFile map (_.delete())
  }

  def enterBarrier(name:String) {
    info(s"[Process: ${processID}] entering barrier: $name")

    if(!lockFolder.exists)
      lockFolder.mkdirs
    val lockFile = new File(lockFolder, s"$name.barrier")
    lockFile.deleteOnExit();
    val l = LArray.mmap(lockFile, 0, numProcesses, MMapMode.READ_WRITE)
    l(processID-1) = 1.toByte
    l.flush

    while(!l.forall(_ == 1.toByte)) {
      Thread.sleep(10)
    }
    info(s"exit barrier: $name")
    l.close
  }
}