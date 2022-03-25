package io.octopus.udp.message;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class DelayMessage implements Delayed {
    private Long messageId;
    private Long time;

    public DelayMessage(Long messageId, Long time) {
        this.messageId = messageId;

        this.time = System.currentTimeMillis() + time;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long diff = time - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        if ((this.time - ((DelayMessage) o).time) == 0) {
            return 0;
        }
        if ((this.time - ((DelayMessage) o).time) > 0) {
            return 1;
        } else {
            return -1;
        }
    }


    public Long getMessageId() {
        return messageId;
    }

}
