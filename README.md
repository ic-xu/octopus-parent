# 概述
##### 单机架构架构
&emsp;&emsp; 考虑到系统的扩展性，业务系统采用可插拔式设计，消息和转发逻辑封装在cote包中，具体的业务逻辑通过插件方式实现。具体如下图所示：

*概念：*

插件管理接口


![](z-doc/插件架构.png)
<center><font size=1>单机内部架构</font></center>



##### 单机和集群之间的通讯组网
&emsp;&emsp; 节点之间的通讯统一由内核封装，这里使用一个接口管理，可以由用户自由实现多种协议插件方式自由扩展服务见通讯方式。

![](z-doc/多节点通讯.png)
<center><font size=1>内部通讯模块架构</font></center>

# 模块分析
- broker-agent：全链路追踪日志收起agent，目前还没实现，只是跑通流程
- broker: 代理转发服务，目前实现了部分mqtt协议消息的代理转发
- broker-console：ssh 与 broker服务交互的sshd服务
- broker-kernel: 内核服务，对应上面的 kernel 内核模块
- broker-route :服务之间路由模块
- broker-rpc: 自定义udp实现的rpc模块，目前实验阶段，还没有正式使用
- broker-store: 存储模块，提供了多种存储插件方式，如H2，levelDB等各种存储方式
- broker-client: client开头的都是客户端相关的，这部分有的是源码，有的还没有实现，后续即将删除
- cluster: 后续即将删除
- mqtt-de-encoder：mqtt协议的编码解码器，增强netty 内置的编码解码器模块。



