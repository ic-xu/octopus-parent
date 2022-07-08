package com.console.sshd;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author chenxu
 * @version 1
 * @date 2022/7/2 16:21
 */
public class SshCommand implements Command {
    private InputStream stdin;
    private OutputStream stdout;
    private OutputStream stderr;
    private ExitCallback exitCallback;
//    private ReplThread repl;

    //
    @Override
    public void setInputStream(InputStream stdin) {
        this.stdin =stdin;
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
    //


    @Override
    public void start(ChannelSession channelSession, Environment environment) throws IOException {

    }

    @Override
    public void destroy(ChannelSession channelSession) throws Exception {

    }
}
