package io.octopus.broker.handler;

import com.google.gson.Gson;
import io.netty.util.internal.StringUtil;
import io.octopus.broker.SessionRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.octopus.broker.subscriptions.ISubscriptionsDirectory;
import io.octopus.broker.subscriptions.Subscription;
import io.octopus.broker.subscriptions.Topic;
import io.octopus.utils.HttpParseUtils;

import java.util.*;


public class NettyHttpServerHandler extends ChannelInboundHandlerAdapter {

    private final SessionRegistry sessions;

    private final ISubscriptionsDirectory subscriptionsDirectory;

    private final Random random = new Random();

    public NettyHttpServerHandler(SessionRegistry sessions, ISubscriptionsDirectory subscriptionsDirectory) {
        this.sessions = sessions;
        this.subscriptionsDirectory = subscriptionsDirectory;
    }


    private String result = "";

    /*
     * 收到消息时，返回信息
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof FullHttpRequest)) {
            result = "未知请求!";
            send(ctx, result, HttpResponseStatus.BAD_REQUEST);
            return;
        }
        FullHttpRequest httpRequest = (FullHttpRequest) msg;
        try {
            String path = httpRequest.uri().split("\\?")[0];          //获取路径
//            String body = getBody(httpRequest);     //获取参数
//            HttpMethod method = httpRequest.method();//获取请求方法
//            DefaultHttpHeaders headers = (DefaultHttpHeaders)httpRequest.headers();
//            String authorization = headers.get("Authorization");
            Map<String, String> parse = HttpParseUtils.parse(httpRequest);
            switch (path) {
                case "/users":
                    String topic = parse.get("topic");
                    if(!StringUtil.isNullOrEmpty(topic)){
                        Set<Subscription> subscriptions = subscriptionsDirectory.matchWithoutQosSharpening(new Topic(topic));
                        List<Map<String,Object>> dataList = new ArrayList<>();
                        subscriptions.forEach(subscription -> {
                            HashMap<String, Object> userInfo = new HashMap<>();
                            userInfo.put("clientId",subscription.getClientId());
                            userInfo.put("id",random.nextInt(10000));
                            dataList.add(userInfo);
                        });
                        send(ctx, dataList, HttpResponseStatus.OK);
                    }
                    break;
                case "":
                    break;
                default:
                    send(ctx, sessions.getClientIdByUsername("admin"), HttpResponseStatus.OK);
                    break;

            }
        } catch (Exception e) {
            System.out.println("处理请求失败!");
            e.printStackTrace();
        } finally {
            //释放请求
            httpRequest.release();
        }
    }

    /**
     * 获取body参数
     *
     * @param request
     * @return
     */
    private String getBody(FullHttpRequest request) {
        ByteBuf buf = request.content();
        return buf.toString(CharsetUtil.UTF_8);
    }

    /**
     * 发送的返回值
     *
     * @param ctx     返回
     * @param context 消息
     * @param status  状态
     */
    private void send(ChannelHandlerContext ctx, Object context, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(new Gson().toJson(context), CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /*
     * 建立连接时，返回消息
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
//        System.out.println("连接的客户端地址:" + ctx.channel().remoteAddress());
//        ctx.writeAndFlush("客户端" + InetAddress.getLocalHost().getHostName() + "成功与服务端建立连接！ ");
        super.channelActive(ctx);
    }

}
