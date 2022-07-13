package com.console.sshd.repl;

import com.console.sshd.command.ConsoleCommand;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author chenxu
 * @version 1
 * @date 2022/7/13 18:54
 */
public class RepelRunnable implements Runnable {

    private final ConsoleCommand command;

    private final InputStream stdin;
    private final InputStream in;
    private final OutputStream out;
    private final OutputStream err;
    private final ReadLineEnvironment environment;
    private final String line;
    private final String[] realArgs;

    public RepelRunnable(ConsoleCommand command, InputStream stdin, InputStream in, OutputStream out, OutputStream err,
                         ReadLineEnvironment environment, String line, String[] realArgs) {
        this.command = command;
        this.stdin = stdin;
        this.in = in;
        this.out = out;
        this.err = err;
        this.environment = environment;
        this.line = line;
        this.realArgs = realArgs;
    }


    @Override
    public void run() {
        command.run(stdin, in, out, err, environment, line, realArgs);
        // last command execute finished
        if (out instanceof TerminalOutputStream) {
            synchronized (command) {
                command.setFinished(true);
                command.notifyAll();
            }
        }
    }
}
