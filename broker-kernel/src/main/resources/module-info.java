module broker.kernel {
    exports io.octopus.contants;
    exports io.octopus.config;
    exports io.octopus.kernel;
    exports io.octopus.kernel.utils;
    exports io.octopus.kernel.kernel;
    exports io.octopus.kernel.kernel.security;
    exports io.octopus.kernel.kernel.router;
    exports io.octopus.kernel.kernel.interceptor;
    exports io.octopus.kernel.kernel.message;
    exports io.octopus.kernel.kernel.repository;
    exports io.octopus.kernel.kernel.subscriptions;
    exports io.octopus.kernel.kernel.queue;
    exports io.octopus.kernel.kernel.listener;
    exports io.octopus.kernel.kernel.subscriptions.plugin;
    exports io.octopus.kernel.kernel.subscriptions.plugin.imp;
    exports io.octopus.kernel.checkpoint;

    requires org.slf4j;
    requires io.netty.buffer;
    requires io.netty.codec;
    requires io.netty.common;
    requires io.netty.handler;
    requires io.netty.resolver;
    requires io.netty.transport;
    requires io.netty.transport.epoll.linux.x86_64;
    requires io.netty.transport.unix.common;
    requires io.netty.transport.classes.epoll;

    requires commons.codec;
    requires metrics.core;
    requires com.github.oshi;

    requires java.base;
    requires java.management;
}