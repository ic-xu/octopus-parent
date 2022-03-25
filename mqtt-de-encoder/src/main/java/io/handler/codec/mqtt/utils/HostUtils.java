package io.handler.codec.mqtt.utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class HostUtils {

    public static String  getPath() {
        Enumeration<NetworkInterface> e = null;
        try {
            e = NetworkInterface.getNetworkInterfaces();
            while(e.hasMoreElements()){
                NetworkInterface n = e.nextElement();
                Enumeration<InetAddress> ee = n.getInetAddresses();
                while (ee.hasMoreElements()){
                    InetAddress i = ee.nextElement();
                    if(i instanceof Inet4Address && !i.isLoopbackAddress()){
                        return i.getHostAddress();
                    }
                }
            }
        } catch (SocketException socketException) {
            socketException.printStackTrace();
        }
        return null;
    }
}
