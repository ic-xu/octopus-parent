package io.octopus.kernel.kernel.message;

import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * @author user
 */
public class InFlightPacket implements Delayed {

    private final Short packetId;
    private final long startTime;

   public InFlightPacket(Short packetId, long delayInMilliseconds) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o){
            return true;
        }
        if (!(o instanceof InFlightPacket)){
            return false;
        }
        InFlightPacket that = (InFlightPacket) o;
        return Objects.equals(getPacketId(), that.getPacketId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPacketId());
    }

    public Short getPacketId() {
        return packetId;
    }

    public long getStartTime() {
        return startTime;
    }
}


