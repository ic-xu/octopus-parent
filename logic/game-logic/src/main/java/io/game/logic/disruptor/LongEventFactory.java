package io.game.logic.disruptor;

import com.lmax.disruptor.EventFactory;

/**
 * @author chenxu
 * @version 1
 * @date $ $
 */
public class LongEventFactory implements EventFactory<LongEvent> {

    @Override
    public LongEvent newInstance() {
        return new LongEvent();
    }
}
