package io.octopus.kernel.kernel;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.octopus.kernel.kernel.config.IConfig;
import io.octopus.kernel.kernel.interceptor.ConnectionNotifyInterceptor;
import io.octopus.kernel.kernel.security.IAuthenticator;
import io.octopus.kernel.kernel.security.ReadWriteControl;
import io.octopus.kernel.kernel.subscriptions.ISubscriptionsDirectory;

import java.util.List;
import java.util.Map;

/**
 * @author chenxu
 * @version 1
 * @date 2022/7/12 16:46
 */
public interface ITransport {

    /**
     * 初始化协议
     *
     * @param bossGroup              bossGroup
     * @param workerGroup            workerGroup
     * @param config                 config
     * @param sessionRegistry        sessionRegistry
     * @param subscriptionsDirectory subscriptionsDirectory
     * @param msgDispatcher          msgDispatcher
     * @param ports                  ports
     * @param authenticator          authenticator
     * @param interceptor            interceptor
     * @param readWriteControl       readWriteControl
     */
    void initProtocol(final EventLoopGroup bossGroup, final EventLoopGroup workerGroup, final Class<? extends ServerSocketChannel> channelClass,
                      final IConfig config, final ISessionResistor sessionRegistry,
                      final ISubscriptionsDirectory subscriptionsDirectory,
                      final IPostOffice msgDispatcher,
                      final Map<String, Integer> ports, final IAuthenticator authenticator,
                      final List<ConnectionNotifyInterceptor> interceptor, final ReadWriteControl readWriteControl);
}
