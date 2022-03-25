package io.octopus.udp.lock;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class CountLock {


    public static void main(String[] args) {
        ReentrantLock reentrantLock = new ReentrantLock();

        Condition condition = reentrantLock.newCondition();

        AtomicInteger atomicInteger = new AtomicInteger(10);

        new Thread(() -> {
            while (true) {
                reentrantLock.lock();
                try {
                    if (atomicInteger.get() > 0) {
                        atomicInteger.decrementAndGet();
                        System.out.println("线程1 执行。。。。。。。。。");
                    } else {
                        try {
                            condition.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {

                } finally {
                    reentrantLock.unlock();
                }
            }
        }).start();

        new Thread(() -> {

            while (true) {

                reentrantLock.lock();
                try {
                    if (atomicInteger.get() > 0) {
                        atomicInteger.decrementAndGet();
                        System.out.println("线程2 执行。。。。。。。。。");
                    } else {
                        try {
                            condition.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {

                } finally {
                    reentrantLock.unlock();
                }
            }
        }).start();


        Scanner scanner = new Scanner(System.in);
        while (true) {
            String s = scanner.nextLine();
            reentrantLock.lock();
            try {
                atomicInteger.incrementAndGet();
                condition.signalAll();
            } catch (Exception e) {

            } finally {
                reentrantLock.unlock();
            }


        }

    }
}
