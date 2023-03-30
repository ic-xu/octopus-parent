package io.octopus.kernel.kernel.disruptor;

import com.lmax.disruptor.EventFactory;

public class ByteArrEventFactory implements EventFactory<MessageArr> {
    @Override
    public MessageArr newInstance() {
        return new MessageArr();
    }
}
