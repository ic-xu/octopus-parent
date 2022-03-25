package io.store;

import org.junit.Test;

import java.util.ArrayList;
import java.util.TreeMap;

public class H2Store {

    @Test
    public void testLong(){

/**
 * |2|0|0|0|0|5|
 */

        TreeMap<Integer, String> integerStringTreeMap = new TreeMap<>();
        integerStringTreeMap.put(11,"222");
        integerStringTreeMap.put(1,"222");
        integerStringTreeMap.put(2,"222");
        integerStringTreeMap.put(4,"222");

        integerStringTreeMap.forEach((key,value) -> System.out.println("key:"+key +"   value : "+value));
    }


}
