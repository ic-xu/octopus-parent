package io.octopus.kernel.utils;


import org.junit.jupiter.api.Test;

public class ObjectUtilsTest {

    @Test
    public void isEmpty() {
    }


    @Test
    public void testTryFinally(){
        try {
            System.out.println("try ---");
            return;
        }finally {
            System.out.println("finally ---");
        }
    }
}