package com.octopus.server.rtmp;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import com.octopus.core.Jazmin;
import com.octopus.core.JazminThreadFactory;
import com.octopus.core.Server;
import com.octopus.misc.InfoBuilder;
//import com.octopus.server.console.ConsoleServer;
import com.octopus.server.rtmp.rtmp.RtmpConfig;
import com.octopus.server.rtmp.util.Utils;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

/**
 *
 */
public class RtmpServer extends Server {
    private static final Map<String, ServerApplication> applications = new ConcurrentHashMap<>();
    private static ChannelFactory factory;
    private static final List<ServerHandler> handlers = Collections.synchronizedList(new ArrayList<>());
    ;
    static RtmpSessionListener sessionListener;

    //
    public RtmpServer() {

    }

    //--------------------------------------------------------------------------
    //
    public void setPort(int port) {
        if (isStarted()) {
            throw new IllegalStateException("set before started");
        }
        RtmpConfig.serverPort = port;
    }

    //
    public void setServerHome(String home) {
        if (isStarted()) {
            throw new IllegalStateException("set before started");
        }
        File homeDir = new File(home);
        if (!homeDir.exists()) {
            throw new IllegalArgumentException("home dir:" + home + " not exists");
        }
        if (homeDir.isFile()) {
            throw new IllegalArgumentException("home dir:" + home + " is not directory");
        }
        RtmpConfig.homeDir = home;
    }

    //
    public String getServerHome() {
        return RtmpConfig.homeDir;
    }

    //
    public int getPort() {
        return RtmpConfig.serverPort;
    }

    //
    public List<ServerApplication> getApplications() {
        return new ArrayList<>(applications.values());
    }

    //
    public List<ServerHandler> getHandlers() {
        return new ArrayList<>(handlers);
    }

    /**
     * @return the sessionListener
     */
    public RtmpSessionListener getSessionListener() {
        return sessionListener;
    }

    /**
     * @param sessionListener the sessionListener to set
     */
    public void setSessionListener(RtmpSessionListener sessionListener) {
        RtmpServer.sessionListener = sessionListener;
    }

    //--------------------------------------------------------------------------
    static void addHandler(ServerHandler handler) {
        handlers.add(handler);
    }

    //删除处理器就
    static void removeHandler(ServerHandler handler) {
        handlers.remove(handler);
    }

    //
    static ServerApplication getApplication(final String rawName) {
        final String appName = Utils.trimSlashes(rawName).toLowerCase();
        ServerApplication app = RtmpServer.applications.get(appName);
        if (app == null) {
            app = new ServerApplication(appName);
            RtmpServer.applications.put(appName, app);
        }
        return app;
    }

    //--------------------------------------------------------------------------
    @Override
    public void init() throws Exception {
//        ConsoleServer cs = Jazmin.getServer(ConsoleServer.class);
//        if (cs != null) {
//            cs.registerCommand(RtmpServerCommand.class);
//        }
    }

    @Override
    public void start() throws Exception {
        factory = new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(new JazminThreadFactory("RtmpBoss")),
                Executors.newCachedThreadPool(new JazminThreadFactory("RtmpWorker")));
        final ServerBootstrap bootstrap = new ServerBootstrap(factory);
        bootstrap.setPipelineFactory(new ServerPipelineFactory());
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);
        final InetSocketAddress socketAddress = new InetSocketAddress(RtmpConfig.serverPort);
        bootstrap.bind(socketAddress);
    }

    //
    @Override
    public void stop() {
        factory.releaseExternalResources();
    }

    //
    @Override
    public String info() {
        InfoBuilder ib = InfoBuilder.create();
        ib.section("info")
                .format("%-30s:%-30s\n")
                .print("port", getPort())
                .print("serverHome", getServerHome())
                .print("sessionListener", getSessionListener());
        return ib.toString();
    }
}
