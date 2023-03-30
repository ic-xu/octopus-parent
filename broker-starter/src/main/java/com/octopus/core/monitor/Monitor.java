/**
 * 
 */
package com.octopus.core.monitor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.alibaba.fastjson.JSON;
import com.octopus.core.Jazmin;
import com.octopus.core.Lifecycle;
import com.octopus.misc.InfoBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author yama
 * 9 Jun, 2016
 */
public class Monitor extends Lifecycle implements Runnable{
	private static Logger logger= LoggerFactory.getLogger(Monitor.class);
	//
	private String monitorUrl;
	private Thread monitorThead;
	private List<MonitorAgent>monitorAgents;
	public static final String CATEGORY_TYPE_KV="KeyValue";
	public static final String CATEGORY_TYPE_VALUE="Value";
	public static final String CATEGORY_TYPE_COUNT="Count";
	//


	// 获取客户端HttpClient
	HttpClient httpClient ;

	HttpResponse.BodyHandler<String> responseBodyHandler ;
	public Monitor() {
		monitorAgents=new ArrayList<MonitorAgent>();
		registerAgent(new VMMonitorAgent());
		registerAgent(new DispatcherMonitorAgent());
		registerAgent(new OSMonitorAgent());
	}
	//
	public void registerAgent(MonitorAgent agent){
		monitorAgents.add(agent);
	}

	//
	public void sample(String categoryName,String type,Map<String,String>kv){
		String json= JSON.toJSONString(kv);
		try {
			post(categoryName,type,json);
		} catch (ExecutionException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	//
	@Override
	public void start() throws Exception {
		if(monitorUrl==null){
			return;
		}
		httpClient = HttpClient.newHttpClient();

		//
		for(MonitorAgent ma:monitorAgents){
			try{
				ma.start(this);
			}catch(Exception e){
				logger.error("{}",e);
			}
		}
		//
		monitorThead=new Thread(this);
		monitorThead.start();
	}
	//
	@Override
	public void run() {
		int idx=0;
		while(true){
			try {
				Thread.sleep(10*1000L);
			} catch (InterruptedException e) {
				logger.error("{}",e);
			}
			idx++;
			for(MonitorAgent ma:monitorAgents){
				try{
					ma.sample(idx,this);
				}catch(Exception e){
					logger.error("{}",e);
				}
			}
		}
	}
	//
	/**
	 * @return the monitorUrl
	 */
	public String getMonitorUrl() {
		return monitorUrl;
	}

	/**
	 * @param monitorUrl the monitorUrl to set
	 */
	public void setMonitorUrl(String monitorUrl) {
		this.monitorUrl = monitorUrl;
	}

	//
	private void post(String name,String type,String data) throws ExecutionException, InterruptedException {
		if(monitorUrl==null){
			return;
		}
		String instanceName=Jazmin.getServerName();
		long time=System.currentTimeMillis();
		String requestUrl = monitorUrl
				+"?instance="+instanceName
				+"&time="+time
				+"&type="+type
				+"&name="+name;
		responseBodyHandler = HttpResponse.BodyHandlers.ofString();
		HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(data);
		HttpRequest request = HttpRequest.newBuilder(URI.create(requestUrl)).POST(bodyPublisher).build();
		httpClient.sendAsync(request,responseBodyHandler).get();
	}
	//
	@Override
	public String info() {
		InfoBuilder ib=InfoBuilder.create().format("%-5s%-30s\n");
		ib.println("monitorUrl:"+monitorUrl);
		ib.section("agents");
		int idx=1;
		for(MonitorAgent ma:monitorAgents){
			ib.print(idx++,ma.getClass().getName());
		}
		return ib.toString();
	}
}
