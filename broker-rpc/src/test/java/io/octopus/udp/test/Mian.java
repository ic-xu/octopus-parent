package io.octopus.udp.test;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Mian {


    public static void printThreadId() throws InterruptedException {

        ReentrantLock firstLock = new ReentrantLock();
        ReentrantLock secLock = new ReentrantLock();
        ReentrantLock threeLock = new ReentrantLock();

        Condition firstThread = firstLock.newCondition();
        Condition secThread = secLock.newCondition();
        Condition threeThread = threeLock.newCondition();


        Thread first = new Thread(() -> {

            while (true) {
                try {
                    firstLock.lock();

                    System.out.println(Thread.currentThread().getName());
                    secLock.lock();
                    secThread.signalAll();
                    secLock.unlock();
                    firstThread.await();
                } catch (InterruptedException e) {

                } finally {
                    firstLock.unlock();

                }
            }

        });
        first.setName("A");
        first.start();

        Thread sec = new Thread(() -> {
            while (true) {
                try {
                    secLock.lock();
                    secThread.await();
                    System.out.println(Thread.currentThread().getName());
                    threeThread.signalAll();
                } catch (InterruptedException e) {

                } finally {
                    secLock.unlock();
                }
            }
        });
        sec.setName("B");
        sec.start();


        Thread three = new Thread(() -> {
            while (true) {
                try {
                    threeLock.lock();
                    threeThread.await();
                    System.out.println(Thread.currentThread().getName());
                    firstThread.signalAll();
                } catch (InterruptedException e) {

                } finally {
                    threeLock.unlock();
                }
            }
        });
        three.setName("C");
        three.start();

        three.join();
    }


    public static void main(String[] args) throws InterruptedException {
        printThreadId();
    }


}
