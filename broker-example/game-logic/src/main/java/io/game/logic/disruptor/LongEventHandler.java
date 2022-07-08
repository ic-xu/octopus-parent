package io.game.logic.disruptor;

import com.lmax.disruptor.EventHandler;

/**
 * @author chenxu
 * @version 1
 */
public class LongEventHandler implements EventHandler<LongEvent> {

    public void onEvent(LongEvent event, long sequence, boolean endOfBatch) {
        System.out.println(Thread.currentThread().getName() + "Event: " + event);
    }
}