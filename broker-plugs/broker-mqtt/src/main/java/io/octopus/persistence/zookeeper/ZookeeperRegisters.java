//package io.octopus.persistence.zookeeper;
//
//
//import io.octopus.kernel.kernel.config.IConfig;
//import io.octopus.kernel.kernel.contants.BrokerConstants;
//import io.octopus.kernel.kernel.router.IRouterRegister;
//import org.I0Itec.zkclient.IZkChildListener;
//import org.I0Itec.zkclient.IZkStateListener;
//import org.I0Itec.zkclient.ZkClient;
//import org.apache.zookeeper.Watcher;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.net.*;
//import java.util.*;
//
//public class ZookeeperRegisters implements IRouterRegister {
//
//
//    Logger logger = LoggerFactory.getLogger(this.getClass().getName());
//
//    private IConfig config;
//    private final ZkClient zkClient;
//    private final String rootPath;
//    private final Integer port;
//    private volatile Set<InetSocketAddress> inetSocketAddresses = new HashSet<>();
//    public static String splitRegex = ":";
//
//    private List<String> localAddress;
//
//    public ZookeeperRegisters(IConfig config) throws SocketException {
//        this.config = config;
//        this.zkClient = new ZkClient(config.getProperty("zookeeper.address"),
//                config.getIntegerProperty("zookeeper.session-timeout", 1000),
//                config.getIntegerProperty("zookeeper.connect-timeout", 3000));
//        rootPath = config.getProperty("zookeeper.root-path");
//        this.port = config.getIntegerProperty("udp.port", BrokerConstants.UDP_TRANSPORT_DEFAULT_PORT);
//        zkClient.subscribeStateChanges(new ZookeeperStatusListener());
//        zkClient.subscribeChildChanges(rootPath, new ZKChildListener());
//        if(!zkClient.exists(rootPath)){
//            zkClient.createPersistent(rootPath);
//        }
//        registerSelf();
//    }
//
//    public void registerSelf() throws SocketException {
//        if (null == localAddress) {
//            localAddress = getPath();
//        }
//
//        for (String path:localAddress) {
//            String zkPath = rootPath+"/"+path;
//            if (!zkClient.exists(zkPath)){
//                zkClient.createEphemeral(zkPath, "ff");
//                logger.info("register path ===[ {} ]=== to zookeeper server success ,", path);
//            }
//
//        }
//    }
//
//    private void getCurrentPathChild(List<String> children) {
//        if (children != null) {
//            HashSet<InetSocketAddress> inetSocketAddressesCopy = new HashSet<>();
//            for (String address:children) {
//                //剔除本机地址
//                if (!localAddress.contains(address)) {
//                    String[] addr = address.split(splitRegex);
//                    if (addr.length == 2) {
//                        int port = Integer.parseInt(addr[1]);
//                        inetSocketAddressesCopy.add(new InetSocketAddress(addr[0], port));
//                    }
//                }
//            }
//            inetSocketAddresses = inetSocketAddressesCopy;
//        }
//    }
//
//    public void stop() throws SocketException {
//        for (String path:getPath()) {
//            zkClient.delete(path);
//        }
//        localAddress = null;
//    }
//
//    @Override
//    public boolean checkIpExit(InetSocketAddress address) {
//        return inetSocketAddresses.contains(address);
//    }
//
//
//    private List<String> getPath() throws SocketException {
//        Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
//        List<String> ipList = new ArrayList<>();
//        while (e.hasMoreElements()) {
//            NetworkInterface n = e.nextElement();
//            Enumeration<InetAddress> ee = n.getInetAddresses();
//            while (ee.hasMoreElements()) {
//                InetAddress i = ee.nextElement();
//                if (i instanceof Inet4Address && !i.isLoopbackAddress())
//                    ipList.add(i.getHostAddress() + splitRegex + port);
//            }
//        }
//        return ipList;
//    }
//
//
//    @Override
//    public Set<InetSocketAddress> getAddressByTopic(String topicName) {
//
//        return inetSocketAddresses;
//    }
//
//    @Override
//    public void saveAddressAndTopic(String topicName, InetSocketAddress address) {
//
//    }
//
//    @Override
//    public void remove(InetSocketAddress address) {
//
//    }
//
//    class ZookeeperStatusListener implements IZkStateListener {
//
//        Logger logger = LoggerFactory.getLogger(this.getClass().getName());
//
//        @Override
//        public void handleStateChanged(Watcher.Event.KeeperState state) {
//            String stateStr = null;
//            switch (state) {
//                case ConnectedReadOnly:
//                    break;
//                case Disconnected:
//                    stateStr = "Disconnected";
//                    break;
//                case Expired:
//                    stateStr = "Expired";
//                    break;
//                case NoSyncConnected:
//                    stateStr = "NoSyncConnected";
//                    break;
//                case SyncConnected:
//                    stateStr = "SyncConnected";
//                    break;
//                case Unknown:
//                default:
//                    stateStr = "Unknow";
//                    break;
//            }
//            logger.info("[Callback]State changed to [ {} ]", stateStr);
//        }
//
//        @Override
//        public void handleNewSession() throws SocketException {
//            registerSelf();
//            getCurrentPathChild(zkClient.getChildren(rootPath));
//        }
//
//        @Override
//        public void handleSessionEstablishmentError(Throwable error) {
//
//        }
//    }
//
//
//    class ZKChildListener implements IZkChildListener {
//        Logger logger = LoggerFactory.getLogger(this.getClass().getName());
//
//        @Override
//        public void handleChildChange(String parentPath, List<String> currentChilds) throws Exception {
//            getCurrentPathChild(currentChilds);
//        }
//    }
//
////    class ZKDataListener implements IZkDataListener {
////        Logger logger = LoggerFactory.getLogger(this.getClass().getName());
////
////        @Override
////        public void handleDataChange(String dataPath, Object data) throws Exception {
////
////        }
////
////        @Override
////        public void handleDataDeleted(String dataPath) throws Exception {
////
////        }
////    }
//}
//
