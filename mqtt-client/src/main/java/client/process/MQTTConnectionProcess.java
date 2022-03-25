package client.process;

import client.MqttCallback;
import client.SessionRegister;
import client.constant.Constant;
import client.sync.SyncRequest;
import client.sync.SyncRequestManager;
import io.handler.codec.mqtt.*;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static io.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE;

/**
 * message handle core
 */
public final class MQTTConnectionProcess {


    private SessionRegister sessionRegister;
    private Channel channel;
    private MqttCallback callback;
    private volatile boolean connected;
    private final AtomicInteger lastPacketId = new AtomicInteger(0);

    public MQTTConnectionProcess(SessionRegister sessionRegister, Channel channel, MqttCallback callback) {
        this.sessionRegister = sessionRegister;
        this.channel = channel;
        this.callback = callback;
    }

    public void handleMessage(MqttMessage msg) {
        MqttMessageType messageType = msg.fixedHeader().messageType();
        switch (messageType) {
            case CUSTOMER:
                processCustomerMessage((MqttCustomerMessage) msg, callback);
                break;
            case CONNACK:
                processConnAck((MqttConnAckMessage) msg, callback);
                break;
            case SUBACK:
                processSubAck((MqttSubAckMessage) msg);
                break;
            case UNSUBACK:
                processUnSubAck((MqttUnsubAckMessage) msg);
                break;
            case PUBLISH:
                processPublish((MqttPublishMessage) msg, callback);
                break;
            case PUBACK:
                processPubAck((MqttPubAckMessage) msg);
                break;
            case PUBREC:
                processPubRec((MqttPubRecMessage)msg);
                break;
            case PUBCOMP:
                processPubComp((MqttPubCompMessage)msg);
                break;
            case PUBREL:
                processPubRel((MqttPubRelMessage)msg);
                break;
            case DISCONNECT:
                processDisconnect(msg);
                break;
            case PINGREQ:
                MqttFixedHeader pingHeader = new MqttFixedHeader(MqttMessageType.PINGRESP, false, AT_MOST_ONCE,
                    false, 0);
                MqttMessage pingResp = new MqttMessage(pingHeader);
                break;
            case PINGRESP:
            case AUTH:
            default:
                break;
        }
    }

    private void processCustomerMessage(MqttCustomerMessage msg, MqttCallback callback) {
        if (null != msg && null != msg.variableHeader() && null != msg.payload())
            callback.customerMessageArrived(msg);
    }


    private void processConnAck(MqttConnAckMessage msg, MqttCallback callback) {
        MqttConnAckVariableHeader mqttConnAckVariableHeader = msg.variableHeader();
        switch (mqttConnAckVariableHeader.connectReturnCode()) {
            case CONNECTION_ACCEPTED:
                callback.connectComplete(false, (InetSocketAddress) channel.remoteAddress());
                return;
            case CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD:
                callback.connectError(Constant.CONNECTION_ACK_PASSWORD_ERR);
                break;
            case CONNECTION_REFUSED_IDENTIFIER_REJECTED:
                callback.connectError(Constant.CONNECTION_ACK_CLIENTID_BANNED);
                break;
            case CONNECTION_REFUSED_SERVER_UNAVAILABLE:
                callback.connectError(Constant.CONNECTION_ACK_SERVER_UNAVAILABLE);
                break;
            case CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION:
                callback.connectError(Constant.CONNECTION_ACK_MQTT_VERSION_UNAVAILABLE);
                break;
            case CONNECTION_REFUSED_NOT_AUTHORIZED:
                callback.connectError(Constant.CONNECTION_ACK_UNAUTHORIZED_LOGIN);
                break;
            default:
                break;
        }

        channel.close();
    }


    private void processSubAck(MqttSubAckMessage msg) {
        SyncRequest requestBody = SyncRequestManager.INSTANCE.getRequestById((short) msg.variableHeader().messageId());
        if (null != requestBody) {
            requestBody.putResponse(msg);
            requestBody.wakeUp();
        }
    }

    private void processUnSubAck(MqttUnsubAckMessage msg) {
        SyncRequest requestBody = SyncRequestManager.INSTANCE.getRequestById((short) msg.variableHeader().messageId());
        if (null != requestBody) {
            requestBody.putResponse(msg);
            requestBody.wakeUp();
        }
    }


    private void processPublish(MqttPublishMessage msg, MqttCallback callback) {
    }


    private void processPubRec(MqttPubRecMessage msg) {
        SyncRequest requestBody = SyncRequestManager.INSTANCE.getRequestById((short) msg.variableHeader().messageId());
        if (null != requestBody) {
            requestBody.putResponse(msg);
            requestBody.wakeUp();
        }
    }


    private void processPubComp(MqttPubCompMessage msg) {
        SyncRequest requestBody = SyncRequestManager.INSTANCE.getRequestById((short) msg.variableHeader().messageId());
        if (null != requestBody) {
            requestBody.putResponse(msg);
            requestBody.wakeUp();
        }
    }


    private void processPubRel(MqttPubRelMessage msg) {
        SyncRequest requestBody = SyncRequestManager.INSTANCE.getRequestById((short) msg.variableHeader().messageId());
        if (null != requestBody) {
            requestBody.putResponse(msg);
            requestBody.wakeUp();
        }
    }


    private void processDisconnect(MqttMessage msg) {
    }


    private void processPubAck(MqttPubAckMessage msg) {

        SyncRequest requestBody = SyncRequestManager.INSTANCE.getRequestById((short) msg.variableHeader().messageId());
        if (null != requestBody) {
            requestBody.putResponse(msg);
            requestBody.wakeUp();
        }

//        MqttQoS mqttQoS = msg.fixedHeader().qosLevel();
//        switch (mqttQoS) {
//            case EXACTLY_ONCE:
//                MqttMessage mqttMessage = MqttMessageFactory.newMessage(new MqttFixedHeader(MqttMessageType.PUBREC, false, AT_MOST_ONCE, false, 2),
//                    (short) msg.variableHeader().messageId(), null);
//                channel.writeAndFlush(mqttMessage);
//                break;
//            case AT_LEAST_ONCE:
//                SyncRequest requestBody = SyncRequestManager.INSTANCE.getRequestById((short) msg.variableHeader().messageId());
//                if (null != requestBody) {
//                    requestBody.putResponse(msg);
//                    requestBody.wakeUp();
//                }
//                break;
//            default:
//                throw new RuntimeException("qos is error  " + mqttQoS.value());
//        }

    }

}
