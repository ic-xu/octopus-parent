package io.octopus.utils;

import io.handler.codec.mqtt.MqttMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.octopus.scala.broker.mqtt.server.MQTTConnection;

import java.io.IOException;

/**
 * Some Netty's channels utilities.
 */
public final class NettyUtils {

    public static final String ATTR_USERNAME = "username";

    private static final String ATTR_CLIENTID = "ClientID";
    private static final String CLEAN_SESSION = "removeTemporaryQoS2";
    private static final String KEEP_ALIVE = "keepAlive";
    private static final AttributeKey<Object> ATTR_KEY_KEEPALIVE = AttributeKey.valueOf(KEEP_ALIVE);
    private static final AttributeKey<Object> ATTR_KEY_CLEANSESSION = AttributeKey.valueOf(CLEAN_SESSION);
    private static final AttributeKey<Object> ATTR_KEY_CLIENTID = AttributeKey.valueOf(ATTR_CLIENTID);
    private static final AttributeKey<Object> ATTR_KEY_USERNAME = AttributeKey.valueOf(ATTR_USERNAME);

    private static final String ATTR_CONNECTION = "connection";
    private static final AttributeKey<Object> ATTR_KEY_CONNECTION = AttributeKey.valueOf(ATTR_CONNECTION);


    public static void bindMqttConnection(Channel channel, MQTTConnection connection) {
        channel.attr(ATTR_KEY_CONNECTION).set(connection);
    }

    public static MQTTConnection getMQTTConnection2Channel(Channel channel) {
        return (MQTTConnection) channel.attr(ATTR_KEY_CONNECTION).get();
    }

    public static Object getAttribute(ChannelHandlerContext ctx, AttributeKey<Object> key) {
        Attribute<Object> attr = ctx.channel().attr(key);
        return attr.get();
    }

    public static void keepAlive(Channel channel, int keepAlive) {
        channel.attr(NettyUtils.ATTR_KEY_KEEPALIVE).set(keepAlive);
    }

    public static void cleanSession(Channel channel, boolean cleanSession) {
        channel.attr(NettyUtils.ATTR_KEY_CLEANSESSION).set(cleanSession);
    }

    public static boolean cleanSession(Channel channel) {
        return (Boolean) channel.attr(NettyUtils.ATTR_KEY_CLEANSESSION).get();
    }

    public static void clientID(Channel channel, String clientID) {
        channel.attr(NettyUtils.ATTR_KEY_CLIENTID).set(clientID);
    }

    public static String clientID(Channel channel) {
        return (String) channel.attr(NettyUtils.ATTR_KEY_CLIENTID).get();
    }

    public static void userName(Channel channel, String username) {
        channel.attr(NettyUtils.ATTR_KEY_USERNAME).set(username);
    }

    public static String userName(Channel channel) {
        return (String) channel.attr(NettyUtils.ATTR_KEY_USERNAME).get();
    }

    /**
	 * Validate that the provided message is an MqttMessage and that it does not contain a failed result.
	 *
	 * @param message to be validated
	 * @return the casted provided message
	 * @throws IOException in case of an fail message this will wrap the root cause
	 * @throws ClassCastException if the provided message is no MqttMessage
	 */
	public static MqttMessage validateMessage(Object message) throws IOException, ClassCastException {
		MqttMessage msg = (MqttMessage) message;
		if (msg.decoderResult() != null && msg.decoderResult().isFailure()) {
			throw new IOException("invalid massage", msg.decoderResult().cause());
		}
		if (msg.fixedHeader() == null) {
			throw new IOException("Unknown packet, no fixedHeader present, no cause provided");
		}
		return msg;
	}

	private NettyUtils() {
    }
}
