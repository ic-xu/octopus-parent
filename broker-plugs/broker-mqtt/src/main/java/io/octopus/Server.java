package io.octopus;

import io.octopus.kernel.kernel.IServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;

/**
 * @author chenxu
 * @version 1
 * @date 2022/7/2 15:06
 */
public class Server {
    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

    public static void main(String[] args) throws Exception {
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        System.setProperty("add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED");

        ServiceLoader<IServer> servers = ServiceLoader.load(IServer.class);
        //

        /// 初始化服务
        for (IServer server:servers) {
            LOGGER.info("init {} ",server.serviceDetails());
            server.init();
        }

        /// 启动服务
        for (IServer server:servers) {
            LOGGER.info("start {} ",server.serviceDetails());
            server.start(args);
        }

        //Bind a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            /// 启动服务
            for (IServer server:servers) {
                LOGGER.info("start {} ",server.serviceDetails());
                try {
                    server.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }));

    }
}
