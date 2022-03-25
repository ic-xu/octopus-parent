/*
 * Copyright (c) 2012-2018 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.octopus.broker.handler;

import io.octopus.base.utils.NettyUtils;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.handler.codec.mqtt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.octopus.broker.Utils.messageId;
import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;

/**
 * @author andrea
 */
@Sharable
public class MQTTMessageLoggerHandler extends ChannelDuplexHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MQTTMessageLoggerHandler.class);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
        logMQTTMessageRead(ctx, message);
        ctx.fireChannelRead(message);
    }

    private void logMQTTMessageRead(ChannelHandlerContext ctx, Object message) throws Exception {
        logMQTTMessage(ctx, message, "C->B");
    }

    private void logMQTTMessageWrite(ChannelHandlerContext ctx, Object message) throws Exception {
        logMQTTMessage(ctx, message, "C<-B");
    }

    private void logMQTTMessage(ChannelHandlerContext ctx, Object message, String direction) throws Exception {
        if (!(message instanceof MqttMessage)) {
            return;
        }
        MqttMessage msg = NettyUtils.validateMessage(message);
        String clientID = NettyUtils.clientID(ctx.channel());
        MqttMessageType messageType = msg.fixedHeader().messageType();
        switch (messageType) {
            case CONNACK:
            case PINGREQ:
            case PINGRESP:
                LOGGER.debug("{} {} <{}>", direction, messageType, clientID);
                break;
            case CONNECT:
            case DISCONNECT:
                LOGGER.info("{} {} <{}>", direction, messageType, clientID);
                break;
            case SUBSCRIBE:
                MqttSubscribeMessage subscribe = (MqttSubscribeMessage) msg;
                LOGGER.info("{} SUBSCRIBE <{}> to topics {}", direction, clientID,
                    subscribe.payload().topicSubscriptions());
                break;
            case UNSUBSCRIBE:
                MqttUnsubscribeMessage unsubscribe = (MqttUnsubscribeMessage) msg;
                LOGGER.info("{} UNSUBSCRIBE <{}> to topics <{}>", direction, clientID, unsubscribe.payload().topics());
                break;
            case PUBLISH:
                MqttPublishMessage publish = (MqttPublishMessage) msg;
                LOGGER.debug("{} PUBLISH <{}> to topics <{}>", direction, clientID, publish.variableHeader().topicName());
                break;
            case PUBREC:
                MqttPubRecMessage pubRecMessage = (MqttPubRecMessage) msg;
                LOGGER.info("{} SUBACK <{}> packetID <{}>, grantedQos {}", direction, clientID, messageId(msg),
                    pubRecMessage.fixedHeader().qosLevel());
                break;
            case PUBCOMP:
                MqttPubCompMessage pubCompMessage = (MqttPubCompMessage) msg;
                LOGGER.info("{} SUBACK <{}> packetID <{}>, grantedQos {}", direction, clientID, messageId(msg),
                    pubCompMessage.fixedHeader().qosLevel());
                break;
            case PUBREL:
                MqttPubRelMessage pubRelMessage = (MqttPubRelMessage) msg;
                LOGGER.info("{} SUBACK <{}> packetID <{}>, grantedQos {}", direction, clientID, messageId(msg),
                    pubRelMessage.fixedHeader().qosLevel());
                break;
            case PUBACK:
                MqttPubAckMessage pubAckMessage = (MqttPubAckMessage) msg;
                LOGGER.info("{} SUBACK <{}> packetID <{}>, grantedQos {}", direction, clientID, messageId(msg),
                    pubAckMessage.fixedHeader().qosLevel());
                break;
            case UNSUBACK:
                MqttUnsubAckMessage unsubAckMessage = (MqttUnsubAckMessage) msg;
                LOGGER.info("{} SUBACK <{}> packetID <{}>, grantedQos {}", direction, clientID, messageId(msg),
                    unsubAckMessage.fixedHeader().qosLevel());
                break;
            case SUBACK:
                MqttSubAckMessage suback = (MqttSubAckMessage) msg;
                final List<Integer> grantedQoSLevels = suback.payload().grantedQoSLevels();
                LOGGER.info("{} SUBACK <{}> packetID <{}>, grantedQos {}", direction, clientID, messageId(msg),
                    grantedQoSLevels);
                break;
            default:
                break;
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String clientID = NettyUtils.clientID(ctx.channel());
        if (clientID != null && !clientID.isEmpty()) {
            LOGGER.info("Channel closed <{}>", clientID);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        logMQTTMessageWrite(ctx, msg);
        ctx.write(msg, promise).addListener(CLOSE_ON_FAILURE);
    }
}
