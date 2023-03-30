package com.octopus.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yama
 * @date Jun 21, 2014
 */
public class JazminThreadFactory implements ThreadFactory{
	private static Logger logger= LoggerFactory.getLogger(JazminThreadFactory.class);
	private AtomicInteger threadCounter=new AtomicInteger();
	private String threadName;
	public JazminThreadFactory(String name) {
		threadName=name;
	}
	//
	@Override
	public Thread newThread(Runnable r) {
		Thread t=new Thread(r);
		t.setContextClassLoader(Jazmin.getAppClassLoader());
		t.setName(threadName+"-"+threadCounter.incrementAndGet());
		Thread.UncaughtExceptionHandler logHander= (t1, e) -> logger.error(e.getMessage(),e);
		t.setUncaughtExceptionHandler(logHander);
		return t;
	}
}
