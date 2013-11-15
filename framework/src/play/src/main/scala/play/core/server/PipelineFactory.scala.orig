package play.core.server

import com.flipkart.phantom.runtime.impl.server.netty.ChannelHandlerPipelineFactory
import org.jboss.netty.handler.codec.http.{HttpContentDecompressor, HttpResponseEncoder, HttpRequestDecoder}
import com.typesafe.netty.http.pipelining.HttpPipeliningHandler
import play.core.server.netty.PlayDefaultUpstreamHandler
import play.core.ApplicationProvider
import org.jboss.netty.channel.ChannelPipeline

/** Created by bfattakhov 2013 */
class PipelineFactory(applicationProvider: ApplicationProvider,
                      defaultUpStreamHandler: PlayDefaultUpstreamHandler, secure: Boolean = false) extends ChannelHandlerPipelineFactory {

  override def getPipeline: ChannelPipeline = {
    val newPipeline =super.getPipeline
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
}
