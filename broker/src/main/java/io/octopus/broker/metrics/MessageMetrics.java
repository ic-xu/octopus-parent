package io.octopus.broker.metrics;

public class MessageMetrics {

    private long m_messagesRead;
    private long m_messageWrote;

    void incrementRead(long numMessages) {
        m_messagesRead += numMessages;
    }

    void incrementWrote(long numMessages) {
        m_messageWrote += numMessages;
    }

    public long messagesRead() {
        return m_messagesRead;
    }

    public long messagesWrote() {
        return m_messageWrote;
    }
}
