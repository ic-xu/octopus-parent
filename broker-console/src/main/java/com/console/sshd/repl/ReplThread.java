package com.console.sshd.repl;


import com.console.sshd.ConsoleServer;
import com.console.sshd.command.ConsoleCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * @author <a href="mailto:trygvis@java.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
public class ReplThread implements Closeable {
	private static Logger logger= LoggerFactory.getLogger(ReplThread.class);
    private final InputStream stdin;
    private final OutputStream stderr;
    private final Thread thread;

    public ReplThread(
    				ConsoleServer consoleServer,
    				final TerminalInputStream stdin, 
    				final OutputStream stdout, 
    				final OutputStream stderr,
                    final ReadLineEnvironment environment, 
                    final Map<String, ConsoleCommand> commands,
                    final Runnable onExit) {
        this.stdin = stdin;
        this.stderr = stderr;

        thread = new Thread(()->{
        try {
        	String prompt=
        			"\033[32;49;1m"+"octopus"+"\033[39;49;0m"+
        			"\033[33;49;1m@"+environment.user+"\033[39;49;0m"+
                    "\033[35;49;1m# "+"server"+" \033[39;49;0m"+
        			">";
        	Repel.repel(consoleServer,stdin, stdout, stderr, environment, commands,prompt);
        } catch (Exception e) {
        	logger.error("{}",e);
        }finally {
            try {
                onExit.run();
            } catch (Exception e) {
                logger.error("{}",e);
            }
        }
        });
        thread.setName("ReplThread");
    }

    public void start() {
        thread.start();
    }

    @Override
    public void close() throws IOException {
//        Repl.closeSilently(stdin);
//        Repl.closeSilently(stderr);
    }



}
