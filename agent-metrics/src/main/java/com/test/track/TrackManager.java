package com.test.track;

public class TrackManager {

    private static final ThreadLocal<Span> trackLocal = new ThreadLocal<>();

    public static void clear(){
        trackLocal.remove();
    }

    public static Span getSpan(){
        return trackLocal.get();
    }

    public static void setSpan(Span span){
        trackLocal.set(span);
    }

}
