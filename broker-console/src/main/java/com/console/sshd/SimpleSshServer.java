package com.console.sshd;

/**
 * @author chenxu
 * @version 1
 * @date 2022/7/2 15:42
 */
public class SimpleSshServer {


    public static void main(String[] args) throws Exception {
        ConsoleServer cs=new ConsoleServer();
        cs.init();
        cs.start();
    }



}