package play.core.server

import play.core.ApplicationProvider
import play.api.Mode
import java.net.InetSocketAddress
import play.api.Play
import java.util.concurrent.Executors
import scala.util.control.NonFatal
import org.jboss.netty.channel.group.DefaultChannelGroup
import java.io.File
import play.core.NamedThreadFactory
import play.core.server.netty.PlayDefaultUpstreamHandler

/** Created by bfattakhov 2013 */
class UDSServer(appProvider: ApplicationProvider, val socketDir: String, val socketName: String, val mode: Mode.Mode = Mode.Prod) extends PhantomUDSNetworkServer with Server with ServerWithStop {

  if (!new File(socketDir, socketName).exists()) {
    throw new Exception(s"Socket file does not exist: $socketDir/$socketName")
  }

  this.setServerExecutors(Executors.newCachedThreadPool(NamedThreadFactory("netty-boss")))
  this.setWorkerExecutors(Executors.newCachedThreadPool(NamedThreadFactory("netty-worker")))

  // Our upStream handler is stateless. Let's use this instance for every new connection
  val defaultUpStreamHandler = new PlayDefaultUpstreamHandler(this, allChannels)

  this.setSocketDir(socketDir)
  this.setSocketName(socketName)
  this.setPipelineFactory(new PipelineFactory(appProvider,defaultUpStreamHandler))

  afterPropertiesSet()

  // Keep a reference on all opened channels (useful to close everything properly, especially in DEV mode)
  val allChannels = new DefaultChannelGroup

  override def mainAddress(): InetSocketAddress = SOCKET._2.getLocalAddress.asInstanceOf[InetSocketAddress]

  def applicationProvider: ApplicationProvider = appProvider

  val SOCKET = {
    this.serverBootstrap = createServerBootstrap()
    val channel = createChannel()
    allChannels.add(channel)
    Play.logger.info(s"Listen for $socketDir/$socketName socket.")
    (this.serverBootstrap, channel)
  }


  override def stop() {

    try {
      Play.stop()
    } catch {
      case NonFatal(e) => Play.logger.error("Error while stopping the application", e)
    }

    try {
      super.stop()
    } catch {
      case NonFatal(e) => Play.logger.error("Error while stopping logger", e)
    }

    mode match {
      case Mode.Test =>
      case _ => Play.logger.info("Stopping server...")
    }

    // First, close all opened sockets
    allChannels.close().awaitUninterruptibly()

    // Release the SOCKET server
    SOCKET._1.releaseExternalResources()

  }

  mode match {
    case Mode.Test =>
    case _ =>
      Play.logger.info("Listening for socket on %s".format(SOCKET._2.getLocalAddress))
  }
}