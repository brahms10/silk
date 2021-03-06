package xerial.silk

import java.io.File
import java.net.InetAddress
import xerial.core.log.Logger
import scala.util.DynamicVariable
import com.netflix.curator.test.ByteCodeRewrite

/**
 * Cluster configuration parameters
 *
 * @author Taro L. Saito
 */
package object cluster extends Logger {

  /**
   * This code is a fix for MXBean unregister problem: https://github.com/Netflix/curator/issues/121
   */
  ByteCodeRewrite.apply()

  //xerial.silk.suppressLog4jwarning



  val localhost: Host = {
    val lh = InetAddress.getLocalHost
    Host(lh.getHostName, lh.getHostAddress)
  }


  // TODO setting configurations from SILK_CONFIG file
  /**
   * A global variable for accessing the configurations using `config.get`
   */
  val configHolder = new DynamicVariable[xerial.silk.cluster.Config](Config())

  def config : Config = configHolder.value

  /**
   * Switch the configurations
   * @param c
   * @param f
   * @tparam U
   * @return
   */
  def withConfig[U](c:Config)(f: => U) : U = {
    info("Swithing a configuration: %s", c)
    configHolder.withValue[U](c)(f)
  }



}
