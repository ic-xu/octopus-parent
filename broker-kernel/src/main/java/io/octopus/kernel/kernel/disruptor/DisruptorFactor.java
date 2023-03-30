package io.octopus.kernel.kernel.disruptor;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.octopus.kernel.utils.ObjectUtils;

public class DisruptorFactor {

    private static final ThreadLocal<Disruptor<MessageArr>> threadDisruptor = new ThreadLocal<>();

    public static Disruptor<MessageArr> newOrGetInstance(EventHandler<MessageArr> handler ) {
        Disruptor<MessageArr> msgDisruptor = threadDisruptor.get();
        if (ObjectUtils.isEmpty(msgDisruptor)) {
            // The factory for the event
            ByteArrEventFactory factory = new ByteArrEventFactory();

            // Specify the size of the ring buffer, must be power of 2.
            int bufferSize = 1024;

            // Construct the Disruptor
            msgDisruptor = new Disruptor<>(factory, bufferSize,
                    new DefaultThreadFactory("disruptor-pool"),
                    ProducerType.SINGLE, new YieldingWaitStrategy());
//                    ProducerType.SINGLE, new LiteTimeoutBlockingWaitStrategy(5, TimeUnit.SECONDS));

            // Connect the handler
            msgDisruptor.handleEventsWith(handler);

            // Start the Disruptor, starts all threads running
            msgDisruptor.start();
            threadDisruptor.set(msgDisruptor);

        }
        return msgDisruptor;

    }


}
