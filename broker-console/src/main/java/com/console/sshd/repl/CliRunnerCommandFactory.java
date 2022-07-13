package com.console.sshd.repl;


import com.console.sshd.ConsoleServer;
import com.console.sshd.command.ConsoleCommand;
import org.apache.sshd.common.channel.PtyMode;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.shell.ShellFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import static java.lang.Integer.valueOf;

/**
 * @author <a href="mailto:trygvis@java.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
public class CliRunnerCommandFactory implements ShellFactory {
    private final Map<String, ConsoleCommand> commands;
    private ConsoleServer consoleServer;

    public CliRunnerCommandFactory(ConsoleServer consoleServer, Map<String, ConsoleCommand> commands) {
        this.consoleServer = consoleServer;
        this.commands = commands;
    }

    @Override
    public Command createShell(ChannelSession channelSession) throws IOException {
        return new OctopusSshCommand();
    }

    //--------------------------------------------------------------------------
    public class OctopusSshCommand implements Command {
        private TerminalInputStream stdin;
        private OutputStream stdout;
        private OutputStream stderr;
        private ExitCallback exitCallback;
        private ReplThread repl;

        //
        @Override
        public void setInputStream(InputStream stdin) {
            this.stdin = new TerminalInputStream(stdin);
        }

        @Override
        public void setOutputStream(OutputStream stdout) {
            this.stdout = stdout;
        }

        @Override
        public void setErrorStream(OutputStream stderr) {
            this.stderr = stderr;
        }

        //
        @Override
        public void setExitCallback(ExitCallback exitCallback) {
            this.exitCallback = exitCallback;
        }


        @Override
        public void start(ChannelSession channelSession, Environment environment) throws IOException {
            repl = new ReplThread(
                    consoleServer,
                    stdin,
                    stdout,
                    stderr,
                    toReadLineEnvironment(environment),
                    commands,
                    () -> exitCallback.onExit(0));
            repl.start();
        }

        @Override
        public void destroy(ChannelSession channelSession) throws Exception {
            Repel.closeSilently(repl);
        }
    }


    //--------------------------------------------------------------------------
    public static boolean getBoolean(Map<PtyMode, Integer> map, PtyMode mode) {
        Integer i = map.get(mode);

        return i != null && i == 1;
    }

    private static Integer getInteger(Map<String, String> env, String key) {
        String s = env.get(key);
        if (s == null) {
            return null;
        }
        try {
            return valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    //--------------------------------------------------------------------------
    public static ReadLineEnvironment toReadLineEnvironment(Environment environment) {
        final Map<String, String> env = environment.getEnv();
        Map<PtyMode, Integer> ptyModes = environment.getPtyModes();
        String encoding = null;
        Integer erase = ptyModes.get(PtyMode.VERASE);
        String user = env.get(Environment.ENV_USER);
        final ReadLineEnvironment readLineEnvironment =
                new ReadLineEnvironment(
                        user,
                        encoding,
                        erase,
                        getBoolean(ptyModes, PtyMode.ICRNL),
                        getBoolean(ptyModes, PtyMode.OCRNL),
                        getInteger(env, Environment.ENV_COLUMNS),
                        getInteger(env, Environment.ENV_LINES));

        environment.addSignalListener((channel, signal) -> {
            readLineEnvironment.onWindowChange(
                    getInteger(env, Environment.ENV_COLUMNS),
                    getInteger(env, Environment.ENV_LINES));
        });

        return readLineEnvironment;
    }
}
