package io.octopus.scala.broker.http

import com.google.gson.Gson
import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil
import io.netty.util.internal.StringUtil
import io.octopus.kernel.kernel.ISessionResistor
import io.octopus.kernel.kernel.subscriptions.{ISubscriptionsDirectory, Topic}
import io.octopus.utils.HttpParseUtils

import java.util
import java.util.Random

/**
 * @author chenxu
 * @version 1
 */

class NettyHttpServerHandler(sessionResistor: ISessionResistor, subscriptionsDirectory: ISubscriptionsDirectory) extends ChannelInboundHandlerAdapter {


  private val random: Random = new Random
  private var result = ""

  /*
   * 收到消息时，返回信息
   */


  override def channelRead(ctx: ChannelHandlerContext, msg: Object): Unit = {
    if (!msg.isInstanceOf[FullHttpRequest]) {
      result = "未知请求!"
      send(ctx, result, HttpResponseStatus.BAD_REQUEST)
      return
    }
    val httpRequest = msg.asInstanceOf[FullHttpRequest]
    try {
      val path = httpRequest.uri.split("\\?")(0) //获取路径
      //            String body = getBody(httpRequest);     //获取参数
      //            HttpMethod method = httpRequest.method();//获取请求方法
      //            DefaultHttpHeaders headers = (DefaultHttpHeaders)httpRequest.headers();
      //            String authorization = headers.get("Authorization");
      val parse = HttpParseUtils.parse(httpRequest)
      path match {
        case "/users" =>
          val topic = parse.get("topic")
          if (!StringUtil.isNullOrEmpty(topic)) {
            val subscriptions = subscriptionsDirectory.matchWithoutQosSharpening(new Topic(topic))
            val dataList = new util.ArrayList[util.Map[String, Any]]
            subscriptions.forEach(subscription => {
              val userInfo = new util.HashMap[String, Any]()
              userInfo.put("clientId", subscription.getClientId)
              userInfo.put("id", random.nextInt(10000))
              dataList.add(userInfo)
            })
            send(ctx, dataList, HttpResponseStatus.OK)
          }

        case "" =>

        case _ =>
          send(ctx, sessionResistor.getClientIdByUsername("admin"), HttpResponseStatus.OK)

      }
    } catch {
      case e: Exception =>
        System.out.println("处理请求失败!")
        e.printStackTrace()
    } finally {
      //释放请求
      httpRequest.release
    }
  }


  /**
   * 获取body参数
   *
   * @param request  req
   * @return
   */
  private def getBody(request: FullHttpRequest):String = {
    val buf = request.content
    buf.toString(CharsetUtil.UTF_8)
  }

  /**
   * 发送的返回值
   *
   * @param ctx     返回
   * @param context 消息
   * @param status  状态
   */
  private def send(ctx: ChannelHandlerContext, context: Object, status: HttpResponseStatus): Unit = {
    val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(new Gson().toJson(context), CharsetUtil.UTF_8))
    response.headers.set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
  }

  /*
   * 建立连接时，返回消息
   */ @throws[Exception]
  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    //        System.out.println("连接的客户端地址:" + ctx.channel().remoteAddress());
    //        ctx.writeAndFlush("客户端" + InetAddress.getLocalHost().getHostName() + "成功与服务端建立连接！ ");
    super.channelActive(ctx)
  }
}
