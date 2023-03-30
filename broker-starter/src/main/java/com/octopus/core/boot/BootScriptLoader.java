/**
 * 
 */
package com.octopus.core.boot;

import com.octopus.utils.IOUtil;

import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;

/**
 * @author yama
 * 27 Dec, 2014
 */
public class BootScriptLoader {
	static {
	    final TrustManager[] trustAllCertificates = new TrustManager[] {
	        new X509TrustManager() {
	            @Override
	            public X509Certificate[] getAcceptedIssuers() {
	                return null; // Not relevant.
	            }
	            @Override
	            public void checkClientTrusted(X509Certificate[] certs, String authType) {
	                // Do nothing. Just allow them all.
	            }
	            @Override
	            public void checkServerTrusted(X509Certificate[] certs, String authType) {
	                // Do nothing. Just allow them all.
	            }
	        }
	    };

	    try {
	        SSLContext sc = SSLContext.getInstance("SSL");
	        sc.init(null, trustAllCertificates, new SecureRandom());
	        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
	    } catch (GeneralSecurityException e) {
	        throw new ExceptionInInitializerError(e);
	    }
	}
	//
	private InputStream inputStream;
	public BootScriptLoader(InputStream input) {
		this.inputStream=input;
	}
	//
	public void load()throws Exception{
		ScriptEngineManager engineManager = new ScriptEngineManager();
		ScriptEngine engine = engineManager.getEngineByName("nashorn");
		SimpleScriptContext ssc=new SimpleScriptContext();

//		//
//		ssc.setAttribute("$",bc, ScriptContext.ENGINE_SCOPE);
//		ssc.setAttribute("jazmin",bc, ScriptContext.ENGINE_SCOPE);
		//
		Bindings bindings=engine.createBindings();
//		bindings.put("$", bc);
//		bindings.put("jazmin",bc);
		engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
		//
		String importScript=
				"load('nashorn:mozilla_compat.js');"+
				"importPackage(Packages.jazmin.core);"+
				"importPackage(Packages.jazmin.core.aop);"+
				"importPackage(Packages.jazmin.core.app);"+
				"importPackage(Packages.jazmin.core.job);"+
				"importPackage(Packages.jazmin.core.monitor);"+
				"importPackage(Packages.jazmin.core.task);"+
				//drivers
				"importPackage(Packages.jazmin.driver.jdbc);"+
				"importPackage(Packages.jazmin.driver.file);"+
				"importPackage(Packages.jazmin.driver.memcached);"+
				"importPackage(Packages.jazmin.driver.mcache);"+
				"importPackage(Packages.jazmin.driver.rpc);"+
				"importPackage(Packages.jazmin.driver.lucene);"+
				"importPackage(Packages.jazmin.driver.process);"+
				"importPackage(Packages.jazmin.driver.redis);"+
				"importPackage(Packages.jazmin.driver.http);"+
				"importPackage(Packages.jazmin.driver.mail);"+
				"importPackage(Packages.jazmin.driver.mongodb);"+
				"importPackage(Packages.jazmin.driver.influxdb);"+
				
				//servers
				"importPackage(Packages.jazmin.server.console);"+
				"importPackage(Packages.jazmin.server.cdn);"+
				"importPackage(Packages.jazmin.server.file);"+
				"importPackage(Packages.jazmin.server.jmx);"+
				"importPackage(Packages.jazmin.server.msg);"+
				"importPackage(Packages.jazmin.server.im);"+
				"importPackage(Packages.jazmin.server.rtmp);"+
				"importPackage(Packages.jazmin.server.sip);"+
				"importPackage(Packages.jazmin.server.rpc);"+
				"importPackage(Packages.jazmin.server.ftp);"+
				"importPackage(Packages.jazmin.server.relay);"+
				"importPackage(Packages.jazmin.server.proxy);"+
				"importPackage(Packages.jazmin.server.webssh);"+
				"importPackage(Packages.jazmin.server.mysqlproxy);"+
				"importPackage(Packages.jazmin.server.websockify);"+
				"importPackage(Packages.jazmin.server.web);\n";
		String script= IOUtil.getContent(inputStream);
		engine.eval(importScript+script, ssc); 
	}
	//
	//
}
