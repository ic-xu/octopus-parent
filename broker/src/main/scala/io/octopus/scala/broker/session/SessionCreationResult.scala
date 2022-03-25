package io.octopus.scala.broker.session

import io.octopus.broker.session.CreationModeEnum

/**
 * @author chenxu
 * @version 1
 */

class SessionCreationResult(session: Session, mode: CreationModeEnum, alreadyStored: Boolean) {

  def getSession: Session = session

  def getMode: CreationModeEnum = mode

  def getAlreadyStored:Boolean = alreadyStored

}
