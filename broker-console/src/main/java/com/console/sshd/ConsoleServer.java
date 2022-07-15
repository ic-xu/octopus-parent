/**
 *
 */
package com.console.sshd;

import com.console.sshd.command.VMCommand;
import com.console.sshd.repl.CliRunnerCommandFactory;
import com.console.sshd.command.ConsoleCommand;
import io.octopus.kernel.kernel.listener.LifecycleListener;
import io.octopus.kernel.kernel.IServer;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.ProcessShellCommandFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author yama
 * 26 Dec, 2014
 */
public class ConsoleServer implements IServer {
    private static final Logger logger = LoggerFactory.getLogger(ConsoleServer.class);
    //
    int port = 2222;
    SshServer sshServer;
    Authenticator authenticator;
    Map<String, ConsoleCommand> commands = new HashMap<>();
    LinkedList<String> commandHistory = new LinkedList<String>();
    int maxCommandHistory;
    SimplePasswordAuthenticator defaultAuthenticator;

    //
    private void startSshServer() throws Exception {
        maxCommandHistory = 500;
        sshServer = SshServer.setUpDefaultServer();
        //sshServer. we just need 2 nio worker for service
        sshServer.setNioWorkers(2);
        sshServer.setPort(port);
        URL resource = SimpleSshServer.class.getResource("/static/jch.ser");
        SimpleGeneratorHostKeyProvider keyProvider = new SimpleGeneratorHostKeyProvider(Path.of(resource.getPath()));
        keyProvider.setAlgorithm(KeyUtils.RSA_ALGORITHM);
        sshServer.setKeyPairProvider(keyProvider);
        defaultAuthenticator = new SimplePasswordAuthenticator();
        defaultAuthenticator.setUser("admin", "admin");
        authenticator = defaultAuthenticator;
        sshServer.setPasswordAuthenticator((u, p, session) -> {
            String loginUser = u;
            session.setUsername(loginUser);
            InetSocketAddress sa = (InetSocketAddress) session.getIoSession().getRemoteAddress();
            String loginHostAddress = sa.getAddress().getHostAddress();
            logger.warn("console server login {}@{}", loginUser, loginHostAddress);
            if (authenticator != null) {
                return authenticator.auth(u, p);
            } else {
                return true;
            }
        });
        loadCommandHistory();
        //
        loadCommand();
        //
        resetBanner();
        sshServer.setShellFactory(new CliRunnerCommandFactory(this, commands));
        sshServer.setCommandFactory(new ScpCommandFactory());
        //设置sftp子系统
        sshServer.setSubsystemFactories(Arrays.asList(new SftpSubsystemFactory()));

        //设置sfp默认的访问目录
        URL rootUrl = SimpleSshServer.class.getResource("/static/root");
        sshServer.setFileSystemFactory(new VirtualFileSystemFactory(Path.of(rootUrl.getPath()).toAbsolutePath()));
        sshServer.setCommandFactory(new ProcessShellCommandFactory());
        sshServer.start();
    }

    //
    public List<ConsoleCommand> commands() {
        return new ArrayList<ConsoleCommand>(commands.values());
    }

    //
    private void resetBanner() {
        String welcomeMsg =
                "----------------------------------------------------------------------\r\n"
                        + "\r\n"
                        + "\r\n"
                        + "\t\tWelcome to JazminServer\r\n"
                        + "\t\ttype 'help' for more information.\r\n"
                        + "----------------------------------------------------------------------\r\n";
        //
        sshServer.getProperties().put("welcome-banner", welcomeMsg);
    }

    //
    private void loadCommand() {
        //
//        registerCommand(new JazCommand());
//        registerCommand(new JazminCommand());
        registerCommand(new VMCommand());
//        registerCommand(new WhoCommand());
//        registerCommand(new HistoryCommand());
//        registerCommand(new HelpCommand());
//        registerCommand(new EchoCommand());
//        registerCommand(new GrepCommand());
//        registerCommand(new SortCommand());
//        registerCommand(new HeadCommand());
//        registerCommand(new TailCommand());
//        registerCommand(new WcCommand());
//        registerCommand(new LessCommand());
//        registerCommand(new UniqCommand());
//        registerCommand(new ManCommand());
//        registerCommand(new NlCommand());
//        registerCommand(new DateCommand());
//        registerCommand(new UpTimeCommand());
//        registerCommand(new CutCommand());
//        registerCommand(new TRCommand());

    }

    //--------------------------------------------------------------------------
    void loadCommandHistory() {
        URL resource = SimpleSshServer.class.getResource("/static");
        assert resource != null;
        String historyFilePath = resource.getPath() + "/.console-history";
        File historyFile = new File(historyFilePath);
        if (historyFile.exists()) {
            try {
                Files.lines(historyFile.toPath()).forEach((s) -> {
                    addCommandHistory(s);
                });
            } catch (IOException e) {
                logger.warn("can not creat read history file {}", historyFile);
            }
        }
    }

    //
    void saveCommandHistory() throws Exception {
        URL resource = SimpleSshServer.class.getResource("/static");
        assert resource != null;
        String historyFilePath = resource.getPath() + "/.console-history";
        File historyFile = new File(historyFilePath);
        if (!historyFile.exists()) {
            if (!historyFile.createNewFile()) {
                logger.error("can not create {} file", historyFilePath);
                return;
            }
        }
        try (BufferedWriter historyWriter = new BufferedWriter(new FileWriter(historyFile, false));) {
            for (String s : commandHistory) {
                historyWriter.write(s + "\n");
            }
            historyWriter.flush();
            historyWriter.close();
        }
    }

    //
    public void addCommandHistory(String line) {
        commandHistory.add(line);
        if (commandHistory.size() > maxCommandHistory) {
            commandHistory.removeFirst();
        }
    }

    public void clearHistory() {
        commandHistory.clear();
    }

    //
    public List<String> getCommandHistory() {
        return new LinkedList<String>(commandHistory);
    }
    //

    /**
     * register command to console server
     * @param commandClass
     */
    public void registerCommand(Class<? extends ConsoleCommand> commandClass) {
        try {
            registerCommand(commandClass.newInstance());
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    //
    private void registerCommand(ConsoleCommand cmd) {
        if (commands.containsKey(cmd.getId())) {
            throw new IllegalArgumentException("cmd :" + cmd.getId() + " already exists.");
        }
        commands.put(cmd.getId(), cmd);
    }

    //
    public ConsoleCommand getCommand(String name) {
        return (ConsoleCommand) commands.get(name);
    }

    //
    public List<ConsoleCommand> getCommands() {
        return new ArrayList<ConsoleCommand>(commands.values());
    }

    //
    public List<ConsoleSession> getConsoleSession() {
        List<ConsoleSession> sessions = new ArrayList<ConsoleSession>();
        sshServer.getActiveSessions().forEach(session -> {
            InetSocketAddress sa = (InetSocketAddress) session.getIoSession().getRemoteAddress();
            String loginHostAddress = sa.getAddress().getHostAddress();
            ConsoleSession s = new ConsoleSession();
            s.user = session.getUsername();
            s.remoteHost = loginHostAddress;
            s.remotePort = sa.getPort();
            sessions.add(s);
        });
        return sessions;
    }

    /**
     * @return the defaultAuthenticator
     */
    public SimplePasswordAuthenticator getDefaultAuthenticator() {
        return defaultAuthenticator;
    }

    /**
     *
     * @param a
     */
    public void setAuthenticator(Authenticator a) {
        this.authenticator = a;
    }

    /**
     * @return the maxCommandHistory
     */
    public int getMaxCommandHistory() {
        return maxCommandHistory;
    }

    /**
     * @param maxCommandHistory the maxCommandHistory to set
     */
    public void setMaxCommandHistory(int maxCommandHistory) {
        this.maxCommandHistory = maxCommandHistory;
    }
    //

    @Override
    public void init() throws Exception {

    }

    //--------------------------------------------------------------------------
    @Override
    public void start() throws Exception {
        startSshServer();
    }

    //
    @Override
    public void stop() throws Exception {
        saveCommandHistory();
        if (sshServer != null) {
            sshServer.stop();
        }
    }

    @Override
    public void destroy() throws Exception {

    }


    @Override
    public String serviceDetails() {
        InfoBuilder ib = InfoBuilder.create()
                .format("%-30s:%-30s\n")
                .print("port", port)
                .print("authenticator", authenticator);
        ib.section("commands");
        commands.forEach((s, cmd) -> {
            ib.print(s, cmd.getClass().getName());
        });
        return ib.toString();
    }

    @Override
    public List<LifecycleListener> lifecycleListenerList() {
        return null;
    }
}
