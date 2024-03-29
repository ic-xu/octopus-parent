package com.console.sshd.repl;

import com.console.sshd.ConsoleServer;
import com.console.sshd.ascii.TerminalWriter;
import com.console.sshd.command.ConsoleCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

/**
 * @author <a href="mailto:trygvis@java.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
public class Repel {
    private static Logger logger = LoggerFactory.getLogger(Repel.class);

    static class CommandEnv {
        InputStream in;
        OutputStream out;
        OutputStream err;
        String line;
        @Override
        public String toString() {
            return "CommandEnv [in=" + in + ", out=" + out + ", err=" + err
                    + ", line=" + line + "]";
        }
    }

    //
    public static int repel(ConsoleServer consoleServer, TerminalInputStream stdin,
                            OutputStream stdout, OutputStream stderr, ReadLineEnvironment environment,
                            Map<String, ConsoleCommand> commands, String prompt) throws IOException {

        String line;

        while (true) {
            ReadLine readLine = new ReadLine(stdin, stdout, environment);
            line = readLine.readLine(prompt, new CommandCompleter(commands));
            if (line == null) {
                break;
            }
            line = line.trim();
            if ("".equals(line)) {
                continue;
            }
            if ("exit".equals(line) || "quit".equals(line)) {
                readLine.println("@_@ ~Bye!");
                readLine.flush();
                break;
            }
            //
            if ("cls".equals(line)) {
                readLine.println(TerminalWriter.CLS);
                readLine.println(TerminalWriter.GOTO1_1);
                readLine.flush();
                continue;
            }
            //
            //process pipe
            String lineList[] = line.split("\\|");
            PipedInputStream nextInputStream = null;
            List<CommandEnv> envList = new ArrayList<Repel.CommandEnv>();
            for (int i = 0; i < lineList.length; i++) {
                CommandEnv env = new CommandEnv();
                env.line = lineList[i];
                if (nextInputStream == null) {
                    env.in = stdin;
                } else {
                    env.in = nextInputStream;
                }
                env.err = new TerminalOutputStream(stderr, environment.ocrnl);
                //last command
                if (i == lineList.length - 1) {
                    env.out = new TerminalOutputStream(stdout, environment.ocrnl);
                } else {
                    nextInputStream = new PipedInputStream();
                    env.out = new PipedOutputStream(nextInputStream);
                }
                envList.add(env);
            }
            //
            stdin.setBreak(false);
            for (CommandEnv env : envList) {
                if (!runCommand( consoleServer, commands, environment,  env.line, stdin, env.in, env.out, env.err)) {
                    readLine.println(TerminalWriter.FRED + "Unknown command '"
                            + env.line + "'." + TerminalWriter.RESET);
                    stdin.setBreak(true);
                    break;
                }
            }
        }
        return 10;
    }

    //
    private static ConsoleCommand getCommand(
            ConsoleServer consoleServer,
            String id, Map<String, ConsoleCommand> commands) {
        ConsoleCommand cc = commands.get(id);
        if (cc == null) {
            return null;
        }
        try {
            ConsoleCommand c = cc.getClass().newInstance();
            c.setConsoleServer(consoleServer);
            return c;
        } catch (Throwable e) {
            logger.error("{}", e.getMessage());
            return null;
        }
    }

    //
    private static boolean runCommand(ConsoleServer consoleServer,
                                      Map<String, ConsoleCommand> commands, ReadLineEnvironment environment,
                                      String cmdline, InputStream stdin, InputStream in, OutputStream out, OutputStream err) {
        String line = cmdline.trim();
        String[] args = line.split(" ");

        ConsoleCommand command = getCommand(consoleServer, args[0].trim(), commands);

        if (command == null) {
            return false;
        }
        String[] realArgs = new String[args.length - 1];
        System.arraycopy(args, 1, realArgs, 0, realArgs.length);

        //run command in new thread
        Thread commandThread = new Thread(() -> {
            command.run(stdin, in, out, err, environment, line, realArgs);
            // last command execute finished
            if (out instanceof TerminalOutputStream) {
                synchronized (command) {
                    command.setFinished(true);
                    command.notifyAll();
                }
            }
        });
        commandThread.setName("Command-" + line);
        commandThread.start();


        //wait for last command
        if (out instanceof TerminalOutputStream) {
            try {
                synchronized (command) {
                    while (!command.isFinished()) {
                        command.wait(1000);
                    }
                }
            } catch (InterruptedException e) {
                logger.error("{}", e.getMessage());
            }
        }
        return true;
    }

    //
    public static class CommandCompleter {
        private final Map<String, ConsoleCommand> commands;

        CommandCompleter(Map<String, ConsoleCommand> commands) {
            this.commands = commands;
        }

        //
        public List<String> complete(String string, int position) {

            int index = string.indexOf(' ');

            // Figure out if we're completing a command name or arguments to the command

            if (index == -1 || index > position) {
                return completeStrings(commands.keySet(), string);
            } else {
                return emptyList();
            }
        }

    }

    //
    public static List<String> completeStrings(Collection<String> strings, String string) {
        List<String> matches = new ArrayList<String>();
        for (String s : strings) {
            if (s.startsWith(string)) {
                matches.add(s);
            }
        }
        return matches;
    }
    //

    /**
     * Returns null.
     */
    public static void closeSilently(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            logger.error("{}", e.getMessage());
        }
    }
}
