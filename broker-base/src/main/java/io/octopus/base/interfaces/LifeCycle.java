package io.octopus.base.interfaces;

import java.io.IOException;

/**
 * container life cycle
 *
 *
 * @author user
 */
public interface LifeCycle {

    /**
     * start life or the container
     */
   default void start() throws IOException {}

    /**
     * start life or the container
     */
   default void start(String[] args){}


    /**
     * stop life or container
     */
   default void stop(){};

}
