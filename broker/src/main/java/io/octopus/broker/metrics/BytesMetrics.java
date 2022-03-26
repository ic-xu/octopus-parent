package io.octopus.broker.metrics;

/**
 * @author user
 */
public class BytesMetrics {

    private long mReadBytes;
    private long mWroteBytes;

    public void incrementRead(long numBytes) {
        mReadBytes += numBytes;
    }

    public void incrementWrote(long numBytes) {
        mWroteBytes += numBytes;
    }

    public long readBytes() {
        return mReadBytes;
    }

    public long wroteBytes() {
        return mWroteBytes;
    }
}
