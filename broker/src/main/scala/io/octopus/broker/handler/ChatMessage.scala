package io.octopus.broker.handler

class ChatMessage(var userId: String, val message: String, messageType: Int, direction: Int) {

  def setUserId(userID: String): Unit = {
    userId = userID
  }

  def getUserId: String = userId

  def getMessage: String = message

  def getMessageType: Int = messageType

  def getDirection: Int = direction

}