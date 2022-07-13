package io.octopus.kernel.kernel.queue;

import io.netty.util.internal.ObjectUtil;

/**
 * An index file built for each message is in a user session queue
 * <p>
 * offset  is the starting position of the message  in the whole queue system
 * size is the message length
 * queueName is a queue system Name
 *
 * @author chenxu
 * @version 1
 */
public class SearchData {

    /**
     * clientId of channel
     */
    private final String clientId;

    /**
     * message index
     */
    private final MsgIndex index;


    public SearchData(String clientId, MsgIndex index) {
        this.clientId = clientId;
        ObjectUtil.checkNotNull(index,"index not null");
        this.index = index;
    }


    public MsgIndex getIndex() {
        return index;
    }

    public String getClientId() {
        return clientId;
    }
}
