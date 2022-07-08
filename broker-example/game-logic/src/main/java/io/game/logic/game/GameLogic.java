package io.game.logic.game;


import com.alibaba.fastjson.JSON;
import io.handler.codec.mqtt.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class GameLogic {
    private final static Logger LOGGER = LoggerFactory.getLogger(GameLogic.class);

    static Map<String, Player> pool = new HashMap<>();
    final static Random RANDOM = new Random();

    public static void handlerMessage(MqttCustomerMessage message) {
        GameMessage gameMessage = JSON.parseObject(message.payload().array(), GameMessage.class);
        int i = 22;
        if (null == gameMessage.data.id) {
            gameMessage.data.setId(String.valueOf(i));
        }

        switch (gameMessage.getAction()) {
            case "CREATE":
                Player player = pool.get(gameMessage.data.id);
                if (null == player) {
                    player = new Player(i, gameMessage.data.nick, gameMessage.data.skin, 22,
                            new Position(RANDOM.nextDouble() +30, RANDOM.nextDouble() +20));
                    pool.put(gameMessage.data.id, player);
                }
                player.setLife(100);
                player.setId(i);
                HashMap<String, Object> replay = new HashMap<>();
                replay.put("action", "PLAYER_JOIN");
                replay.put("error", "false");
                replay.put("msg", "ff");
                HashMap<String, Object> data = new HashMap<>();
                data.put("nick", player.getNick());
                data.put("skin", player.getSkin());
                data.put("id", player.getId());
                Map<String, Double> position = new HashMap<>();
                position.put("x", player.getPosition().getX());
                position.put("y", player.getPosition().getY());
                data.put("position", position);
                List<Player> re = new ArrayList<>();
                for (String key : pool.keySet()) {
                    re.add(pool.get(key));
                }
                data.put("playersON", re);
                replay.put("data", data);
//                pool.forEach((key, value) -> value.getConnection().getBoundSession().sendCustomerMessage(wrapperGameMessage(JSON.toJSONString(replay))));
                break;

            case "RECEIVED_DAMAGE":
                Player player1 = pool.get(gameMessage.getData().getPlayerId());
                if (null != player1)
                    player1.setLife(player1.getLife() - gameMessage.getData().getLife());
                if (player1.getLife() <= 0) {
                    pool.remove(gameMessage.getData().getPlayerId());
                    player1.setKills(player1.getKills() + 1);
                }
            case "MOVE":
                Player player2 = pool.get(gameMessage.data.getPlayerId());
                player2.setPosition(gameMessage.data.position);
            case "ATTACK":
//                pool.forEach((key, value) -> value.getConnection().getBoundSession().sendCustomerMessage(wrapperGameMessage(message.payload().array())));
                break;

            default:
                break;
        }
    }


    private static MqttCustomerMessage wrapperGameMessage(String jsonString) {
        LOGGER.info("发送的消息为 {}", jsonString);
        MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(MqttMessageType.CUSTOMER, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttCustomerVariableHeader variableHeader = new MqttCustomerVariableHeader(22);
        ByteBuf buf = Unpooled.wrappedBuffer(jsonString.getBytes(StandardCharsets.UTF_8));

        byte type = 10;
        return new MqttCustomerMessage(mqttFixedHeader, variableHeader, buf, type);
    }


    private static MqttCustomerMessage wrapperGameMessage(byte[] array) {
        MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(MqttMessageType.CUSTOMER, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttCustomerVariableHeader variableHeader = new MqttCustomerVariableHeader(22);
        ByteBuf buf = Unpooled.wrappedBuffer(array);
        byte type = 10;
        return new MqttCustomerMessage(mqttFixedHeader, variableHeader, buf, type);
    }


    public static void disconnect() {

    }
}
