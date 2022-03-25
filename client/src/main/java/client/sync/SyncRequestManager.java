package client.sync;

import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.Map;
import java.util.concurrent.*;

public enum SyncRequestManager {

    INSTANCE;

    private Map<Short, SyncRequest> requestTable = new ConcurrentHashMap<>(8);

    private ExecutorService service = new ThreadPoolExecutor(0, 4,
        60L, TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        new DefaultThreadFactory("Request-Pool"));


    public void addRequest(Short requestId, SyncRequest syncRequest) throws Exception {
        SyncRequest syncRequest1 = requestTable.putIfAbsent(requestId, syncRequest);
        if (null != syncRequest1) {
            throw new Exception("request packageId is exist ! ! !");
        }
    }

    public void removeRequest(Short requestId) {
        requestTable.remove(requestId);
    }


    public ExecutorService getSender() {
        return service;
    }

    public SyncRequest getRequestById(Short id) {
        return requestTable.remove(id);
    }

}
