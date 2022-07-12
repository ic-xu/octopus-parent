package io.octopus.kernel.kernel.metrics;

public class MessageMetrics {

    private long mMessagesRead;
    private long mMessageWrote;

    public void incrementRead(long numMessages) {
        mMessagesRead += numMessages;
    }

    public void incrementWrote(long numMessages) {
        mMessageWrote += numMessages;
    }

    public long messagesRead() {
        return mMessagesRead;
    }

    public long messagesWrote() {
        return mMessageWrote;
    }
}
