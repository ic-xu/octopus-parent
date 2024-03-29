package io.octopus.kernel.kernel.metrics;

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
