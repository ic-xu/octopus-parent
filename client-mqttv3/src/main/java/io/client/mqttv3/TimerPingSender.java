/*******************************************************************************
 * Copyright (c) 2014, 2018 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution. 
 *
 * The Eclipse Public License is available at 
 *    https://www.eclipse.org/legal/epl-2.0
 * and the Eclipse Distribution License is available at 
 *   https://www.eclipse.org/org/documents/edl-v10.php
 */

package io.client.mqttv3;

import java.util.Timer;
import java.util.TimerTask;

import io.client.mqttv3.logging.Logger;
import io.client.mqttv3.logging.LoggerFactory;
import io.client.mqttv3.internal.ClientComms;

/**
 * Default ping sender implementation
 *
 * <p>This class implements the {@link MqttPingSender} pinger interface
 * allowing applications to send ping packet to server every keep alive interval.
 * </p>
 *
 * @see MqttPingSender
 */
public class TimerPingSender implements MqttPingSender {
	private static final String CLASS_NAME = TimerPingSender.class.getName();
	private Logger log = LoggerFactory.getLogger(LoggerFactory.MQTT_CLIENT_MSG_CAT,CLASS_NAME);

	private ClientComms comms;
	private Timer timer;
	private String clientid;

	public void init(ClientComms comms) {
		if (comms == null) {
			throw new IllegalArgumentException("ClientComms cannot be null.");
		}
		this.comms = comms;
		clientid = comms.getClient().getClientId();
		log.setResourceName(clientid);
	}

	public void start() {
		final String methodName = "start";		
		
		//@Trace 659=start timer for client:{0}
		log.fine(CLASS_NAME, methodName, "659", new Object[]{clientid});
				
		timer = new Timer("MQTT Ping: " + clientid);
		//Check ping after first keep alive interval.
		timer.schedule(new PingTask(), comms.getKeepAlive());
	}

	public void stop() {
		final String methodName = "stop";
		//@Trace 661=stop
		log.fine(CLASS_NAME, methodName, "661", null);
		if(timer != null){
			timer.cancel();
		}
	}

	public void schedule(long delayInMilliseconds) {
		timer.schedule(new PingTask(), delayInMilliseconds);		
	}
	
	private class PingTask extends TimerTask {
		private static final String METHOD_NAME = "PingTask.run";
		
		public void run() {
			//@Trace 660=Check schedule at {0}
			log.fine(CLASS_NAME, METHOD_NAME, "660", new Object[]{Long.valueOf(System.nanoTime())});
			comms.checkForActivity();			
		}
	}
}
