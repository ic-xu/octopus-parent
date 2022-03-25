package io.store.message;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class InFlightPacket implements Delayed {

    private final int packetId;
    private final long startTime;

   public InFlightPacket(int packetId, long delayInMilliseconds) {
        this.packetId = packetId;
        this.startTime = System.currentTimeMillis() + delayInMilliseconds;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long diff = startTime - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        if ((this.startTime - ((InFlightPacket) o).startTime) == 0) {
            return 0;
        }
        if ((this.startTime - ((InFlightPacket) o).startTime) > 0) {
            return 1;
        } else {
            return -1;
        }
    }

    public int getPacketId() {
        return packetId;
    }

    public long getStartTime() {
        return startTime;
    }
}


