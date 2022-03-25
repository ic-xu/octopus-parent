package io.store.persistence.flishdisk;

import io.handler.codec.mqtt.IMessage;
import io.octopus.base.config.IConfig;
import io.octopus.base.contants.BrokerConstants;
import io.octopus.base.queue.MsgQueue;
import io.octopus.base.utils.ObjectUtils;
import io.store.persistence.disk.CheckPointServer;
import org.h2.mvstore.MVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FlushDiskServer {

    Logger logger = LoggerFactory.getLogger(FlushDiskServer.class);
    private final ScheduledExecutorService flushDiskService;
    private final MsgQueue<IMessage> concurrentFileQueue;
    private final MVStore mvStore;
    private final int autoSaveInterval; // in seconds
    private final CheckPointServer checkPointServer;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    public FlushDiskServer(MsgQueue<IMessage> concurrentFileQueue, MVStore mvStore,
                           IConfig config, ScheduledExecutorService flushDiskService,
                           CheckPointServer checkPointServer) {
        this.concurrentFileQueue = concurrentFileQueue;
        this.mvStore = mvStore;
        this.flushDiskService = flushDiskService;
        final String autoSaveProp = config.getProperty(BrokerConstants.AUTOSAVE_INTERVAL_PROPERTY_NAME, "10");
        this.autoSaveInterval = Integer.parseInt(autoSaveProp);
        this.checkPointServer = checkPointServer;
    }


    public void start() {
        flushDiskService.scheduleWithFixedDelay(() -> {
            if (null != concurrentFileQueue) {
                logger.trace("{}  flush ot disk", dateFormat.format(System.currentTimeMillis()));
                concurrentFileQueue.flushDisk();
            }

            if (null != mvStore) {
                logger.trace("{}  commit to h2", dateFormat.format(System.currentTimeMillis()));
                mvStore.commit();
            }

            if (!ObjectUtils.isEmpty(concurrentFileQueue)) {
                checkPointServer.saveCheckPoint(concurrentFileQueue.wrapperCheckPoint(), true);
            }
        }, autoSaveInterval, autoSaveInterval, TimeUnit.SECONDS);
    }


}
