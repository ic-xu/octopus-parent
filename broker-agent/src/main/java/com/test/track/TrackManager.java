package com.test.track;

public class TrackManager {

    private static final ThreadLocal<Span> THREAD_LOCAL = new ThreadLocal<>();

    public static void clear(){
        THREAD_LOCAL.remove();
    }

    public static Span getSpan(){
        return THREAD_LOCAL.get();
    }

    public static void setSpan(Span span){
        THREAD_LOCAL.set(span);
    }

}
