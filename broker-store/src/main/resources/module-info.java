module broker.store {
    exports io.store.persistence;
    exports io.store.util;
    exports io.store.persistence.maptree;
    exports io.store.persistence.disk;

    requires broker.kernel;
    requires org.slf4j;
    requires com.h2database.mvstore;
    requires leveldb;
    requires leveldb.api;

    requires io.netty.buffer;
    requires io.netty.codec;
    requires io.netty.common;
    requires io.netty.handler;
    requires io.netty.resolver;
    requires io.netty.transport;
    requires io.netty.transport.epoll.linux.x86_64;
    requires io.netty.transport.unix.common;
    requires io.netty.transport.classes.epoll;


}