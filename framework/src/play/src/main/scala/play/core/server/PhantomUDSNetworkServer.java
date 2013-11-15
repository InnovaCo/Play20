package play.core.server;


import com.flipkart.phantom.netty.uds.OioServerSocketChannel;
import com.flipkart.phantom.netty.uds.OioServerSocketChannelFactory;
import com.flipkart.phantom.runtime.impl.server.concurrent.NamedThreadFactory;
import com.flipkart.phantom.runtime.impl.server.netty.AbstractNettyNetworkServer;
import com.flipkart.phantom.runtime.spi.server.NetworkServer;
import org.jboss.netty.bootstrap.Bootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.springframework.util.Assert;
import org.trpr.platform.core.impl.logging.LogFactory;
import org.trpr.platform.core.spi.logging.Logger;
import org.trpr.platform.runtime.impl.config.FileLocator;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <code>UDSNetworkServer</code> is a concrete implementation of the {@link AbstractNettyNetworkServer}
 * for Unix Domain Sockets. Uses {@link OioServerSocketChannel} for UDS channels. Note that this server
 * has to be initialized with a UDS socket file rather than a port no.
 *
 * @author devashishshankar
 * @version 1.0, 19 Apr, 2013
 */
public class PhantomUDSNetworkServer extends AbstractNettyNetworkServer {

    /**
     * Logger for this class
     */
    private static final Logger LOGGER = LogFactory.getLogger(PhantomUDSNetworkServer.class);

    /**
     * The default counts (invalid one) for server and worker pool counts
     */
    private static final int INVALID_POOL_SIZE = -1;

    /**
     * The default directory name containing junix native libraries
     */
    private static final String DEFAULT_JUNIX_NATIVE_DIRECTORY = "uds-lib";

    /**
     * The System property to be set with Junix native lib path
     */
    private static final String JUNIX_LIB_SYSTEM_PROPERTY = "org.newsclub.net.unix.library.path";

    /**
     * The server and worker thread pool sizes
     */
    private int serverPoolSize = INVALID_POOL_SIZE;
    private int workerPoolSize = INVALID_POOL_SIZE;

    /**
     * The server and worker ExecutorService instances
     */
    private ExecutorService serverExecutors;
    private ExecutorService workerExecutors;

    /**
     * The directory name containing junix native libraries
     */
    private String junixNativeLibDirectoryName = DEFAULT_JUNIX_NATIVE_DIRECTORY;

    /**
     * The name of the socket file for this server (UDS)
     */
    private String socketName;

    /**
     * The directory containing the socket file
     */
    private String socketDir;

    /**
     * The socket file
     */
    private File socketFile;

    /**
     * Interface method implementation. Creates server and worker thread pools if required and then calls {@link #afterPropertiesSet()} on the super class
     *
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    public void afterPropertiesSet() throws Exception {
        //Required properties
        Assert.notNull(this.socketDir, "socketDir is a required property for UDSNetworkServer");
        Assert.notNull(this.socketName, "socketName is a required property for UDSNetworkServer");
        //Create the socket file
        this.socketFile = new File(new File(this.socketDir), this.socketName);

        //Create socket address
        LOGGER.info("Socket file: " + this.socketFile.getAbsolutePath());
        try {
            this.socketAddress = new AFUNIXSocketAddress(this.socketFile);
        } catch (IOException e) {
            throw new RuntimeException("Error creating Socket Address. ", e);
        }
        if (this.getServerExecutors() == null) { // no executors have been set for server listener
            if (this.getServerPoolSize() != PhantomUDSNetworkServer.INVALID_POOL_SIZE) { // thread pool size has been set. create and use a fixed thread pool
                this.setServerExecutors(Executors.newFixedThreadPool(this.getServerPoolSize(), new NamedThreadFactory("UDSServer-Listener")));
            } else { // default behavior of creating and using a cached thread pool
                this.setServerExecutors(Executors.newCachedThreadPool(new NamedThreadFactory("UDSServer-Listener")));
            }
        }
        if (this.getWorkerExecutors() == null) {  // no executors have been set for workers
            if (this.getWorkerPoolSize() != PhantomUDSNetworkServer.INVALID_POOL_SIZE) { // thread pool size has been set. create and use a fixed thread pool
                this.setWorkerExecutors(Executors.newFixedThreadPool(this.getWorkerPoolSize(), new NamedThreadFactory("UDSServer-Worker")));
            } else { // default behavior of creating and using a cached thread pool
                this.setWorkerExecutors(Executors.newCachedThreadPool(new NamedThreadFactory("UDSServer-Worker")));
            }
        }
        super.afterPropertiesSet();
        LOGGER.info("UDS Server startup complete");
    }

    @Override
    public String getServerType() {
        return "UDS Netty Service";
    }

    @Override
    public String getServerEndpoint() {
        return this.socketDir + this.socketFile.getAbsolutePath();
    }

    /**
     * Overriden super class method. Returns a readable string for this UDSNetworkServer
     *
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return "UDSNetworkServer [socketFile=" + socketFile.getAbsolutePath() + "] " + this.getPipelineFactory();
    }

    /**
     * Interface method implementation. Creates and returns a Netty ServerBootstrap instance
     *
     * @see com.flipkart.phantom.runtime.impl.server.netty.AbstractNettyNetworkServer#createServerBootstrap()
     */
    protected Bootstrap createServerBootstrap() throws RuntimeException {
        Assert.notNull(this.socketFile, "Socket File should not be null");
        OioServerSocketChannelFactory serverSocketChannelFactory = new OioServerSocketChannelFactory(this.getServerExecutors(), this.getWorkerExecutors());
        serverSocketChannelFactory.setSocketFile(this.socketFile);
        return new ServerBootstrap(serverSocketChannelFactory);
    }

    /**
     * Abstract method implementation. Creates and returns a Netty Channel from the ServerBootstrap that was
     * previously created in {@link PhantomUDSNetworkServer#createServerBootstrap()}
     *
     * @see com.flipkart.phantom.runtime.impl.server.netty.AbstractNettyNetworkServer#createChannel()
     */
    protected Channel createChannel() throws RuntimeException {
        if (this.getServerBootstrap() == null) {
            throw new RuntimeException("Error creating Channel. Bootstrap instance cannot be null. See UDSNetworkServer#createServerBootstrap()");
        }
        return ((ServerBootstrap) this.serverBootstrap).bind(this.socketAddress);
    }

    /**
     * Start Getter/Setter methods
     */
    public int getServerPoolSize() {
        return this.serverPoolSize;
    }

    public void setServerPoolSize(int serverPoolSize) {
        this.serverPoolSize = serverPoolSize;
    }

    public int getWorkerPoolSize() {
        return this.workerPoolSize;
    }

    public void setWorkerPoolSize(int workerPoolSize) {
        this.workerPoolSize = workerPoolSize;
    }

    public ExecutorService getServerExecutors() {
        return this.serverExecutors;
    }

    public void setServerExecutors(ExecutorService serverExecutors) {
        this.serverExecutors = serverExecutors;
    }

    public ExecutorService getWorkerExecutors() {
        return this.workerExecutors;
    }

    public void setWorkerExecutors(ExecutorService workerExecutors) {
        this.workerExecutors = workerExecutors;
    }

    public String getSocketDir() {
        return socketDir;
    }

    public void setSocketDir(String socketDir) {
        this.socketDir = socketDir;
    }

    public String getSocketName() {
        return socketName;
    }

    public void setSocketName(String socketName) {
        this.socketName = socketName;
    }

    public String getJunixNativeLibDirectoryName() {
        return junixNativeLibDirectoryName;
    }

    public void setJunixNativeLibDirectoryName(String junixNativeLibDirectoryName) {
        this.junixNativeLibDirectoryName = junixNativeLibDirectoryName;
    }

    @Override
    public NetworkServer.TransmissionProtocol getTransmissionProtocol() {
        return null;
    }
    /** End Getter/Setter methods */
}