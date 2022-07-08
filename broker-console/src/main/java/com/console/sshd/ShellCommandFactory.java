package com.console.sshd;

import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;

import java.io.IOException;

/**
 * @author chenxu
 * @version 1
 * @date 2022/7/2 16:18
 */
public class ShellCommandFactory implements CommandFactory {


    @Override
    public Command createCommand(ChannelSession channelSession, String s) throws IOException {
        System.out.println("  ===========>   "+s);
        return new SshCommand();
    }
}
