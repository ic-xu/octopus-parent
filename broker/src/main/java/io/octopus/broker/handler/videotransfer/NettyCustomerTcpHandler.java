package io.octopus.broker.handler.videotransfer;

import com.google.gson.Gson;
import io.handler.codec.mqtt.*;
import io.octopus.broker.MqttConnection;
import io.octopus.broker.Session;
import io.octopus.broker.handler.videotransfer.bean.EventData;
import io.octopus.broker.handler.videotransfer.bean.MemCons;
import io.octopus.broker.handler.videotransfer.bean.RoomInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class NettyCustomerTcpHandler {

    private static final String NEW = "new";
    private static final String INVITE = "invite";
    private static final String RING = "ring";
    private static final String CANCEL = "cancel";
    private static final String REJECT = "reject";
    private static final String JOIN = "join";
    private static final String CANDIDATE = "candidate";
    private static final String OFFER = "offer";
    private static final String ANSWER = "answer";
    private static final String LEAVE = "bye";
    private static final String AUDIO = "audio";
    private static final String DISCONNECT = "leave";

    private static final Gson gson = new Gson();


    public static void handleMessage(ByteBuf buf, MqttConnection mqttConnect){
        buf.release();
    }



    // 发送各种消息
    public static void handleMessage(String message, MqttConnection mqttConnect) {
        System.out.println(message);
        EventData data;
        try {
            data = gson.fromJson(message, EventData.class);
        } catch (Exception e) {
            System.out.println("json解析错误：" + message);
            return;
        }
        switch (data.getType()) {
            case NEW:
                createRoom(message, data.getData(),mqttConnect);
                break;
            case INVITE:
                invite(message, data.getData());
                break;
            case RING:
                ring(message, data.getData());
                break;
            case CANCEL:
                cancel(message, data.getData());
                break;
            case REJECT:
                reject(message, data.getData());
                break;
            case JOIN:
                join(message, data.getData());
                break;
            case CANDIDATE:
                iceCandidate(message, data.getData());
                break;
            case OFFER:
                offer(message, data.getData());
                break;
            case ANSWER:
                answer(message, data.getData());
                break;
            case LEAVE:
                leave(message, data.getData());
                break;
            case AUDIO:
                transAudio(message, data.getData());
                break;
            case DISCONNECT:
                disconnet(message, data.getData());
                break;
            default:
                break;
        }

    }

    // 创建房间
    private static void createRoom(String message, Map<String, Object> data, MqttConnection mqttConnect) {
        String room = (String) data.get("id");
        String userId = (String) data.get("name");

        System.out.println(String.format("createRoom:%s ", room));

        RoomInfo roomParam = MemCons.rooms.get(room);
        // 没有这个房间
        if (roomParam == null) {
            int size = (int) Double.parseDouble(String.valueOf(data.get("roomSize")));
            // 创建房间
            RoomInfo roomInfo = new RoomInfo();
            roomInfo.setMaxSize(size);
            roomInfo.setRoomId(room);
            roomInfo.setUserId(userId);
            // 将房间储存起来
            MemCons.rooms.put(room, roomInfo);


            CopyOnWriteArrayList<Session> copy = new CopyOnWriteArrayList<>();
            // 将自己加入到房间里
            Session mySession = MemCons.userBeans.get(userId);
            copy.add(mySession);
            MemCons.rooms.get(room).setSessions(copy);
            EventData send = new EventData();
            send.setType("peers");
            Map<String, Object> map = new HashMap<>();
            map.put("peers", "");
            map.put("self", userId);
            map.put("roomSize", size);
            send.setData(map);
            System.out.println(gson.toJson(send));

            sendMsg(mqttConnect.getBoundSession(), -1, gson.toJson(send));

        }

    }

    // 首次邀请
    private static void invite(String message, Map<String, Object> data) {
        String userList = (String) data.get("userList");
        String room = (String) data.get("room");
        String inviteId = (String) data.get("inviteID");
        boolean audioOnly = (boolean) data.get("audioOnly");
        String[] users = userList.split(",");

        System.out.println(String.format("room:%s,%s invite %s audioOnly:%b", room, inviteId, userList, audioOnly));
        // 给其他人发送邀请
        for (String user : users) {
            Session session = MemCons.userBeans.get(user);
            if (session != null) {
                sendMsg(session, -1, message);
            }
        }


    }

    // 响铃回复
    private static void ring(String message, Map<String, Object> data) {
        String room = (String) data.get("room");
        String inviteId = (String) data.get("toID");

        Session session = MemCons.userBeans.get(inviteId);
        if (session != null) {
            sendMsg(session, -1, message);
        }
    }

    // 取消拨出
    private static void cancel(String message, Map<String, Object> data) {
        String room = (String) data.get("room");
        String userList = (String) data.get("userList");
        String[] users = userList.split(",");
        for (String userId : users) {
            Session session = MemCons.userBeans.get(userId);
            if (session != null) {
                sendMsg(session, -1, message);
            }
        }

        if (MemCons.rooms.get(room) != null) {
            MemCons.rooms.remove(room);
        }


    }

    // 拒绝接听
    private static void reject(String message, Map<String, Object> data) {
        String room = (String) data.get("room");
        String toID = (String) data.get("toID");
        Session session = MemCons.userBeans.get(toID);
        if (session != null) {
            sendMsg(session, -1, message);
        }
        RoomInfo roomInfo = MemCons.rooms.get(room);
        if (roomInfo != null) {
            if (roomInfo.getMaxSize() == 2) {
                MemCons.rooms.remove(room);
            }
        }


    }

    // 加入房间
    private static void join(String message, Map<String, Object> data) {
        String room = (String) data.get("room");
        String userID = (String) data.get("userID");

        RoomInfo roomInfo = MemCons.rooms.get(room);

        int maxSize = roomInfo.getMaxSize();
        CopyOnWriteArrayList<Session> roomSessions = roomInfo.getSessions();

        //房间已经满了
        if (roomSessions.size() >= maxSize) {
            return;
        }
        Session my = MemCons.userBeans.get(userID);
        // 1. 將我加入到房间
        roomSessions.add(my);
        roomInfo.setSessions(roomSessions);
        MemCons.rooms.put(room, roomInfo);

        // 2. 返回房间里的所有人信息
        EventData send = new EventData();
        send.setType("peers");
        Map<String, Object> map = new HashMap<>();

        String[] cons = new String[roomSessions.size()];
        for (int i = 0; i < roomSessions.size(); i++) {
            Session session = roomSessions.get(i);
            if (session.getClientID().equals(userID)) {
                continue;
            }
            cons[i] = session.getClientID();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cons.length; i++) {
            if (cons[i] == null) {
                continue;
            }
            sb.append(cons[i]).append(",");
        }
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        map.put("connections", sb.toString());
        map.put("you", userID);
        map.put("roomSize", roomInfo.getMaxSize());
        send.setData(map);
        sendMsg(my, -1, gson.toJson(send));


        EventData newPeer = new EventData();
        newPeer.setType("new_peer");
        Map<String, Object> sendMap = new HashMap<>();
        sendMap.put("userID", userID);
        newPeer.setData(sendMap);

        // 3. 给房间里的其他人发送消息
        for (Session session : roomSessions) {
            if (session.getClientID().equals(userID)) {
                continue;
            }
            sendMsg(session, -1, gson.toJson(newPeer));
        }


    }

    // 切换到语音接听
    private static void transAudio(String message, Map<String, Object> data) {
        String userId = (String) data.get("userID");
        Session session = MemCons.userBeans.get(userId);
        if (session == null) {
            System.out.println("用户 " + userId + " 不存在");
            return;
        }
        sendMsg(session, -1, message);
    }

    // 意外断开
    private static void disconnet(String message, Map<String, Object> data) {
        String userId = (String) data.get("userID");
        Session session = MemCons.userBeans.get(userId);
        if (session == null) {
            System.out.println("用户 " + userId + " 不存在");
            return;
        }
        sendMsg(session, -1, message);
    }

    // 发送offer
    private static void offer(String message, Map<String, Object> data) {
        String userId = (String) data.get("userID");
        Session session = MemCons.userBeans.get(userId);
        sendMsg(session, -1, message);
    }

    // 发送answer
    private static void answer(String message, Map<String, Object> data) {
        String userId = (String) data.get("userID");
        Session session = MemCons.userBeans.get(userId);
        if (session == null) {
            System.out.println("用户 " + userId + " 不存在");
            return;
        }
        sendMsg(session, -1, message);

    }

    // 发送ice信息
    private static void iceCandidate(String message, Map<String, Object> data) {
        String userId = (String) data.get("userID");
        Session session = MemCons.userBeans.get(userId);
        if (session == null) {
            System.out.println("用户 " + userId + " 不存在");
            return;
        }
        sendMsg(session, -1, message);
    }

    // 离开房间
    private static void leave(String message, Map<String, Object> data) {
        String room = (String) data.get("room");
        String userId = (String) data.get("fromID");
        if (userId == null) return;
        RoomInfo roomInfo = MemCons.rooms.get(room);
        CopyOnWriteArrayList<Session> roomSessions = roomInfo.getSessions();
        Iterator<Session> iterator = roomSessions.iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (userId.equals(session.getClientID())) {
                continue;
            }
            sendMsg(session, -1, message);

            if (roomSessions.size() == 1) {
                System.out.println("房间里只剩下一个人");
                if (roomInfo.getMaxSize() == 2) {
                    MemCons.rooms.remove(room);
                }
            }

            if (roomSessions.size() == 0) {
                System.out.println("房间无人");
                MemCons.rooms.remove(room);
            }
        }


    }


    private static final Object object = new Object();

    // 给不同设备发送消息
    private static void sendMsg(Session session, int device, String str) {
        byte[] bytes = str.getBytes();
        byte[] messageType = new byte[1];
        messageType[0] = 1;
        ByteBuf payload = Unpooled.wrappedBuffer(messageType,bytes);
        if (session != null) {
            synchronized (object) {
                Short packageId = 22;
                MqttCustomerMessage message   = new MqttCustomerMessage( new MqttFixedHeader(MqttMessageType.CUSTOMER,
                        false, MqttQoS.AT_MOST_ONCE,
                        false, payload.readableBytes()), new MqttCustomerVariableHeader(packageId), payload, payload.readByte());
                session.sendCustomerMessage(message);
            }
        }

//
//        } else if (device == 1) {
//            Session pcSession = userBean.getPcSession();
//            if (pcSession != null) {
//                synchronized (object) {
//                    pcSession.getAsyncRemote().sendText(str);
//                }
//            }
//        } else {
//            Session phoneSession = userBean.getPhoneSession();
//            if (phoneSession != null) {
//                synchronized (object) {
//                    phoneSession.getAsyncRemote().sendText(str);
//                }
//            }
//            Session pcSession = userBean.getPcSession();
//            if (pcSession != null) {
//                synchronized (object) {
//                    pcSession.getAsyncRemote().sendText(str);
//                }
//            }
//
//        }

    }

}
