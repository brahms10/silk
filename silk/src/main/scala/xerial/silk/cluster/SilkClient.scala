//--------------------------------------
//
// SilkClient.scala
// Since: 2012/12/13 4:50 PM
//
//--------------------------------------

package xerial.silk.cluster

import com.typesafe.config.ConfigFactory
import akka.actor.{Props, Actor, ActorSystem}
import xerial.core.log.Logger
import xerial.lens.cui.{command, DefaultCommand}
import xerial.silk.cluster.SilkClient.Terminate
import xerial.silk.DefaultMessage
import xerial.core.io.IOUtil

/**
 * SilkClient is a network interface that accepts command from the other hosts
 */
object SilkClient extends Logger {

  def getActorSystem(host:String=localhost.address, port:Int)= {

    val akkaConfig = ConfigFactory.parseString(
      """
        |akka.daemonic = on
        |akka.actor.provider = "akka.remote.RemoteActorRefProvider"
        |akka.remote.transport = "akka.remote.netty.NettyRemoteTransport"
        |akka.remote.netty.hostname = "%s"
        |akka.remote.netty.port = %d
        |      """.stripMargin.format(host, port))


    ActorSystem("silk", akkaConfig, Thread.currentThread.getContextClassLoader)
  }

  lazy val system = {
    info("Starting a new ActorSystem")
    getActorSystem(port = config.silkClientPort)
  }
  lazy val connSystem = {
    info("Starting an ActorSystem for connection")
    getActorSystem(port = IOUtil.randomPort)
  }

  def startClient = {
    val client = system.actorOf(Props(new SilkClient), "SilkClient")
    system.awaitTermination()
  }

  def getClientAt(host:String) = {
    val akkaAddr = "akka://silk@%s:%s/user/SilkClient".format(host, config.silkClientPort)
    debug("remote akka actor address: %s", akkaAddr)
    connSystem.actorFor(akkaAddr)
  }

  sealed trait ClientCommand
  case class Terminate() extends ClientCommand
  case class Status() extends ClientCommand


  case class ClientInfo(m:MachineResource, pid:Int)

}




/**
 * @author Taro L. Saito
 */
class SilkClient extends Actor with Logger {

  import SilkClient._

  override def preStart() = {
    info("SilkClient started at %s", MachineResource.thisMachine.host)
  }

  protected def receive = {
    case Terminate => {
      debug("Recieved terminate signal")
      sender ! "ack"
      context.stop(self)
      context.system.shutdown()
    }
    case Status => {
      sender ! "OK"
    }
    case message => {
      debug("message recieved: %s", message)
      sender ! "hello"
    }
  }
  override def postStop() {
    info("Stopped SilkClient")
  }

}