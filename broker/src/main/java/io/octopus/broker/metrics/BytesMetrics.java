package io.octopus.broker.metrics;

public class BytesMetrics {

    private long m_readBytes;
    private long m_wroteBytes;

    void incrementRead(long numBytes) {
        m_readBytes += numBytes;
    }

    void incrementWrote(long numBytes) {
        m_wroteBytes += numBytes;
    }

    public long readBytes() {
        return m_readBytes;
    }

    public long wroteBytes() {
        return m_wroteBytes;
    }
}
