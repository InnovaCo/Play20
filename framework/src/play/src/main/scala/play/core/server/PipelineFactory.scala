package play.core.server

import com.flipkart.phantom.runtime.impl.server.netty.ChannelHandlerPipelineFactory
import scala.Some
import org.jboss.netty.handler.ssl.SslHandler
import org.jboss.netty.handler.codec.http.{HttpContentDecompressor, HttpResponseEncoder, HttpRequestDecoder}
import com.typesafe.netty.http.pipelining.HttpPipeliningHandler
import javax.net.ssl.{TrustManager, KeyManagerFactory, SSLContext}
import java.security.KeyStore
import java.io.{FileInputStream, File}
import play.api.Play
import scala.util.control.NonFatal
import play.core.server.netty.{PlayDefaultUpstreamHandler, FakeKeyStore}
import play.core.ApplicationProvider

/** Created by bfattakhov 2013 */
class PipelineFactory(applicationProvider: ApplicationProvider,
                      defaultUpStreamHandler: PlayDefaultUpstreamHandler, secure: Boolean = false) extends ChannelHandlerPipelineFactory {

  override def getPipeline = {
    val newPipeline = super.getPipeline()
    if (secure) {
      sslContext.map {
        ctxt =>
          val sslEngine = ctxt.createSSLEngine
          sslEngine.setUseClientMode(false)
          newPipeline.addLast("ssl", new SslHandler(sslEngine))
      }
    }
    val maxInitialLineLength = Option(System.getProperty("http.netty.maxInitialLineLength")).map(Integer.parseInt).getOrElse(4096)
    val maxHeaderSize = Option(System.getProperty("http.netty.maxHeaderSize")).map(Integer.parseInt).getOrElse(8192)
    val maxChunkSize = Option(System.getProperty("http.netty.maxChunkSize")).map(Integer.parseInt).getOrElse(8192)
    newPipeline.addLast("decoder", new HttpRequestDecoder(maxInitialLineLength, maxHeaderSize, maxChunkSize))
    newPipeline.addLast("encoder", new HttpResponseEncoder())
    newPipeline.addLast("decompressor", new HttpContentDecompressor())
    newPipeline.addLast("http-pipelining", new HttpPipeliningHandler())
    newPipeline.addLast("handler", defaultUpStreamHandler)
    newPipeline
  }

  lazy val sslContext: Option[SSLContext] = //the sslContext should be reused on each connection
    Option(System.getProperty("https.keyStore")) map {
      path =>
      // Load the configured key store
        val keyStore = KeyStore.getInstance(System.getProperty("https.keyStoreType", "JKS"))
        val password = System.getProperty("https.keyStorePassword", "").toCharArray
        val algorithm = System.getProperty("https.keyStoreAlgorithm", KeyManagerFactory.getDefaultAlgorithm)
        val file = new File(path)
        if (file.isFile) {
          try {
            for (in <- resource.managed(new FileInputStream(file))) {
              keyStore.load(in, password)
            }
            Play.logger.debug("Using HTTPS keystore at " + file.getAbsolutePath)
            val kmf = KeyManagerFactory.getInstance(algorithm)
            kmf.init(keyStore, password)
            Some(kmf)
          } catch {
            case NonFatal(e) => {
              Play.logger.error("Error loading HTTPS keystore from " + file.getAbsolutePath, e)
              None
            }
          }
        } else {
          Play.logger.error("Unable to find HTTPS keystore at \"" + file.getAbsolutePath + "\"")
          None
        }
    } orElse {

      // Load a generated key store
      Play.logger.warn("Using generated key with self signed certificate for HTTPS. This should not be used in production.")
      Some(FakeKeyStore.keyManagerFactory(applicationProvider.path))

    } flatMap {
      a => a
    } map {
      kmf =>
      // Load the configured trust manager
        val tm = Option(System.getProperty("https.trustStore")).map {
          case "noCA" => {
            Play.logger.warn("HTTPS configured with no client " +
              "side CA verification. Requires http://webid.info/ for client certifiate verification.")
            Array[TrustManager](noCATrustManager)
          }
          case _ => {
            Play.logger.debug("Using default trust store for client side CA verification")
            null
          }
        }.getOrElse {
          Play.logger.debug("Using default trust store for client side CA verification")
          null
        }

        // Configure the SSL context
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.getKeyManagers, tm, null)
        sslContext
    }

}
