package com.test;

import io.handler.codec.mqtt.MqttPublishMessage;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;

/**
 * @author user
 */
public class AgentMain {

    public static void premain(String agentArgs, Instrumentation inst){

//        inst.addTransformer(new TraceTransformer(),true);


        AgentBuilder.Transformer transformer= (builder, typeDescription, classLoader, javaModule) -> {
            // method对所有方法进行拦截
            // intercept添加拦截器
            return builder.method(ElementMatchers.any())
                .intercept(MethodDelegation.to(TraceInterceptor.class));
        };
        // 指定拦截org.pearl.order下
        new AgentBuilder.Default().type(ElementMatchers.nameStartsWith("io.octopus"))
            .transform(transformer).installOn(inst);

        // 指定拦截org.pearl.order下
        new AgentBuilder.Default().type(ElementMatchers.nameStartsWith("io.handler.codec.mqtt"))
            .transform(transformer).installOn(inst);
    }


    public static void agentmain(String agentOps, Instrumentation inst) {
        System.out.println("====agentmain 方法执行");

        AgentBuilder.Transformer transformer= (builder, typeDescription, classLoader, javaModule) -> {
            // method对所有方法进行拦截
            // intercept添加拦截器
            return builder.method(ElementMatchers.takesArguments(MqttPublishMessage.class))
                .intercept(MethodDelegation.to(TraceInterceptor.class));
        };
        // 指定拦截org.pearl.order下
        new AgentBuilder.Default().type(ElementMatchers.nameStartsWith("io.octopus"))
            .transform(transformer).installOn(inst);

        // 指定拦截org.pearl.order下
        new AgentBuilder.Default().type(ElementMatchers.nameStartsWith("io.handler.codec.mqtt"))
            .transform(transformer).installOn(inst);
//        inst.addTransformer(new TraceTransformer(),true);
    }

}
