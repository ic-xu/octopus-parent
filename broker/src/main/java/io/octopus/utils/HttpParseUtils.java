package io.octopus.utils;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HttpParseUtils {

    public static Map<String, String> parse(FullHttpRequest request) {
        HttpMethod method = request.method();
        Map<String, String> parmMap = new HashMap<>();
        if (method == HttpMethod.GET) {
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            decoder.parameters().forEach((key, value) -> parmMap.put(key, value.get(0)));
        } else if (method == HttpMethod.POST) {
            HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(request);
            decoder.offer(request);
            decoder.getBodyHttpDatas().forEach( attribute ->{
                Attribute data = (Attribute) attribute;
                try {
                    parmMap.put(data.getName(),data.getValue());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
        return parmMap;
    }

}
