package com.console.sshd.command;


import com.console.sshd.ConsoleServer;
import com.console.sshd.ascii.TerminalWriter;
import com.console.sshd.repl.DumpUtil;
import com.console.sshd.repl.ReadLineEnvironment;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author yama
 * 26 Dec, 2014
 */
public class ConsoleCommand {
	//
	private static Logger logger= LoggerFactory.getLogger(ConsoleCommand.class);
	//
	public static interface OptionRunnable{
		void run(String input)throws Exception;
	}
	//
	public static class OptionDefine{
		String name;
		boolean hasArgs;
		String desc;
		OptionRunnable runnable;
	}
	//
    protected PrintWriter out;
    protected InputStream stdin;
    protected InputStream inStream;
    protected OutputStream outStream;
    protected OutputStream errStream;
    protected ReadLineEnvironment environment;
    protected String[] args;
    protected String id;
    protected String desc;
    protected CommandLine cli;
    protected String rawInput;
    private volatile boolean finished;
    private Options options;
    ConsoleServer consoleServer;
    //
    private Map<String,OptionDefine>commandOptionMap;
    //
    public boolean isPiped(){
    	return (inStream instanceof PipedInputStream);
    }
    //
    
    //
    public ConsoleCommand(boolean enableLoop) {
    	finished=false;
    	options=new Options();
    	commandOptionMap=new LinkedHashMap<String,OptionDefine>();
    	if(enableLoop){
    		addOption("loop", false, "loop display", null);
    	}
	}
    /**
	 * @return the consoleServer
	 */
	public ConsoleServer getConsoleServer() {
		return consoleServer;
	}

	/**
	 * @param consoleServer the consoleServer to set
	 */
	public void setConsoleServer(ConsoleServer consoleServer) {
		this.consoleServer = consoleServer;
	}

	/**
	 * @return the finished
	 */
	public boolean isFinished() {
		return finished;
	}

	/**
	 * @param finished the finished to set
	 */
	public void setFinished(boolean f) {
		this.finished=f;
	}

	//
    public void addOption(String id,boolean hasArgs,String desc,OptionRunnable runnable){
    	OptionDefine od=new OptionDefine();
    	od.name=id;
    	od.hasArgs=hasArgs;
    	od.desc=desc;
    	od.runnable=runnable;
    	commandOptionMap.put(id, od);
    	Option option=new Option(id, desc);
    	if(od.hasArgs){
    		option.setArgs(Option.UNLIMITED_VALUES);
    	}
    	options.addOption(option);
    }
    //
    public String getId() {
        return id;
    }
    public String getDesc(){
    	return desc;
    }
    //
    public void printHelp(){
    	out.println(getHelpInfo());
    }
    //
    public String getHelpInfo(){
    	StringWriter sw=new StringWriter();
    	PrintWriter pw=new PrintWriter(sw);
    	//Stringwr
      	HelpFormatter formatter = new HelpFormatter();
    	formatter.printHelp(pw,80,id,desc,options,3,3,"",true);
    	pw.flush();
    	pw.close();
    	return sw.toString();
    }
    //
    protected void run() throws Exception{
    	boolean hit=false;
    	for(Entry<String, OptionDefine> e:commandOptionMap.entrySet()){
    		OptionDefine od=e.getValue();
    		String optionName=e.getKey();
    		if(cli.hasOption(optionName)&&od.runnable!=null){
    			hit=true;
    			String args=null;
    			if(e.getValue().hasArgs){
    				String t[]=cli.getOptionValues(e.getKey());
    				StringBuilder sb=new StringBuilder();
    				for(String q:t){
    					sb.append(q+" ");
    				}
    				args=sb.toString().trim();
    			}
    			if(cli.hasOption("loop")){
    				runWithLoop(args,od.runnable::run);
    			}else{
    				od.runnable.run(args);			
    			}
    		}
    	}
    	if(!hit){
    		printHelp();
    	}
    }
    //
    public final void run( InputStream stdin,  InputStream in,  OutputStream out,  OutputStream err,
    		ReadLineEnvironment environment,  String line, String[] args){
    	try{
    		this.rawInput=line;
    		this.stdin=stdin;
	    	this.inStream=in;
	    	this.outStream=out;
	    	this.errStream=err;
	    	this.out = new PrintWriter(outStream);
	    	this.environment = environment;
	    	this.args = args;
        	GnuParser parser=new GnuParser();
			this.cli = parser.parse(options,args);
			consoleServer.addCommandHistory(line);
			run();
		} catch (IOException | UnrecognizedOptionException e2) {
			this.out.println(e2.getMessage());
		} catch (MissingArgumentException e) {
			this.out.println(e.getMessage());
		}catch (Exception e) {
			logger.error("{}",e.getMessage());
			this.out.println(e.getMessage());
		}finally{
			try{
				this.out.flush();
				this.out.close();
			}catch(Exception e){
				logger.error("{}",e.getMessage());
			}
		}
    }
    //
    protected String formatDate(Date date){
    	if(date==null){
    		return "null";
    	}
    	SimpleDateFormat sdf=new SimpleDateFormat("MM/dd HH:mm:ss");
    	return  sdf.format(date);
    }
    //
    protected String cut(String string,int maxLength){
    	return DumpUtil.cut(string, maxLength);
    }
    //
    protected void printLine(char c,int width){
    	for(int i=0;i<width;i++){
    		out.print(c);
    	}
    	out.print("\n");
    }

	/**
	 *
	 * @param args
	 * @param f
	 * @throws Exception
	 */
    protected void runWithLoop(String args,OptionRunnable f)
    		throws Exception{
    	TerminalWriter tw=new TerminalWriter(out);
    	SimpleDateFormat sdf=new SimpleDateFormat("MM/dd HH:mm:ss");
    	while(stdin.available()==0){
			tw.cls();
			tw.bgreen();
    		tw.fmagenta();
    		String now=sdf.format(new Date());
    		int screenWidth=environment.getColumns();
    		out.format("%-30s %"+(screenWidth-31)+"s\n","press any key to quit.",now);
    		tw.reset();
    		f.run(args);
    		out.flush();
    		TimeUnit.SECONDS.sleep(1);
    	}
    }
}
