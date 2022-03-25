/*******************************************************************************
 * Copyright (c) 2009, 2014 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution. 
 *
 * The Eclipse Public License is available at 
 *    https://www.eclipse.org/legal/epl-2.0
 * and the Eclipse Distribution License is available at 
 *   https://www.eclipse.org/org/documents/edl-v10.php
 *
 * Contributors:
 *    Dave Locke - initial API and implementation and/or initial documentation
 */
package io.client.mqttv3.internal.wire;

import io.client.mqttv3.MqttException;
import io.client.mqttv3.MqttPersistable;
import io.client.mqttv3.MqttPersistenceException;

public abstract class MqttPersistableWireMessage extends MqttWireMessage
		implements MqttPersistable {
	
	public MqttPersistableWireMessage(byte type) {
		super(type);
	}
	
	public byte[] getHeaderBytes() throws MqttPersistenceException {
		try {
			return getHeader();
		}
		catch (MqttException ex) {
			throw new MqttPersistenceException(ex.getCause());
		}
	}
	
	public int getHeaderLength() throws MqttPersistenceException {
		return getHeaderBytes().length;
	}

	public int getHeaderOffset() throws MqttPersistenceException{
		return 0;
	}

//	public String getKey() throws MqttPersistenceException {
//		return new Integer(getMessageId()).toString();
//	}

	public byte[] getPayloadBytes() throws MqttPersistenceException {
		try {
			return getPayload();
		}
		catch (MqttException ex) {
			throw new MqttPersistenceException(ex.getCause());
		}
	}
	
	public int getPayloadLength() throws MqttPersistenceException {
		return 0;
	}

	public int getPayloadOffset() throws MqttPersistenceException {
		return 0;
	}

}
