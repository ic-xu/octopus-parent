
##### 单机架构架构
&emsp;&emsp; 考虑到系统的扩展性，业务系统采用可插拔式设计，消息和转发逻辑封装在cote包中，具体的业务逻辑通过插件方式实现。具体如下图所示：

*概念：*

插件管理接口

![](z-doc/单机架构设计.excalidraw.md)
<center><font size=1>单机内部架构</font></center>

##### 单机和集群之间的通讯组网
&emsp;&emsp; 节点之间的通讯统一由内核封装，这里使用一个接口管理，可以由用户自由实现多种协议插件方式自由扩展服务见通讯方式。


![](z-doc/节点之间通讯模块.excalidraw.md)
<center><font size=1>内部通讯模块架构</font></center>