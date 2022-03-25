package io.octopus

import org.slf4j.{Logger, LoggerFactory}

class Server{
 val LOGGER:Logger = LoggerFactory.getLogger(classOf[Server])

  def startServer():Unit = {
    LOGGER.info("{}  Server started, version: {}" ,Version.PROJECT_NAME,Version.VERSION)
  }

  def stopServer():Thread = {
    new Thread(new Runnable() {
      override def run(): Unit = ???
    })
  }

}


object Server {

  def main(args: Array[String]): Unit = {
    val server:Server = new Server()
    server.startServer()
    Runtime.getRuntime.addShutdownHook(server.stopServer())
  }

}
