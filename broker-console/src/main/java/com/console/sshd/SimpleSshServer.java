package com.console.sshd;

/**
 * @author chenxu
 * @version 1
 * @date 2022/7/2 15:42
 */

import org.apache.sshd.common.util.OsUtils;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.ProcessShellFactory;

/**
 *
 * @author user
 */
public class SimpleSshServer {

    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        embedding();
        // 禁止 bouncy castle，避免版本冲突
        System.setProperty("org.apache.sshd.registerBouncyCastle", "false");
        // SSH服务
        final SshServer sshServer = SshServer.setUpDefaultServer();
        // 指定提供ssh服务的IP
//        sshServer.setHost("127.0.0.1");
        // 指定ssh服务的端口
        sshServer.setPort(222);
        // 指定密码认证
        sshServer.setPasswordAuthenticator(
                (username, password, session) -> {
                    System.out.println(">>>>>>> 用户名：" + username);
                    System.out.println(">>>>>>> 密码：" + password);
                    return "root".equals(username) && "root123".equals(password);
                }
        );
        if (OsUtils.isUNIX()) {
            // 在Unix环境下，使用系统自带的sh来执行命令
            sshServer.setShellFactory(getShellFactory4Unix());
        } else {
            sshServer.setShellFactory(getShellFactory4Win());
        }
        sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
//        sshServer.setCommandFactory(new ScpCommandFactory());
//        sshServer.setShellFactory(new InteractiveProcessShellFactory());
        sshServer.setCommandFactory(new ShellCommandFactory());
        sshServer.start();
    }

    /**
     * windows下的Shell
     *
     * @return
     */
    public static ProcessShellFactory getShellFactory4Win() {
        return new ProcessShellFactory();
    }

    /**
     * unix下的Shell
     *
     * @return
     */
    public static ProcessShellFactory getShellFactory4Unix() {
        return new ProcessShellFactory();
    }


    /**
     * 一直有效
     */
    static void embedding() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

}