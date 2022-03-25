package com.test.track;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

public class Span {

    private AtomicInteger spanId = new AtomicInteger(0);

    private Long trackId;

    private Long startTime;

    private Method method;

    public Span(Long trackId,Method method) {
        this.trackId = trackId;
        this.startTime = System.currentTimeMillis();
        this.method = method;
    }

    public int getSpanId() {
        return spanId.get();
    }

    public Method getMethod() {
        return method;
    }

    public void decrement() {
        this.spanId.decrementAndGet() ;
    }

    public void increment() {
        this.spanId.incrementAndGet();
    }

    public Long getTrackId() {
        return trackId;
    }

    public void setTrackId(Long trackId) {
        this.trackId = trackId;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }


}
