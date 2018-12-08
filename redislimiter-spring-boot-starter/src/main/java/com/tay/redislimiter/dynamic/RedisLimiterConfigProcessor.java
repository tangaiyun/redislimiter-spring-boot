/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * @author  Aiyun Tang
 * @mail aiyun.tang@gmail.com
 */
package com.tay.redislimiter.dynamic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tay.redislimiter.RedisLimiterProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public final class RedisLimiterConfigProcessor extends JedisPubSub implements ApplicationContextAware, BeanPostProcessor, InitializingBean {
    Logger logger = LoggerFactory.getLogger(RedisLimiterConfigProcessor.class);

    private final RedisLimiterProperties redisLimiterProperties;


    private String applicationName;

    private ApplicationContext applicationContext;

    private ConcurrentHashMap<String, LimiterConfig> configMap = new ConcurrentHashMap<>();

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet(){
        applicationName = applicationContext.getEnvironment().getProperty("spring.application.name");
        if(applicationName == null) {
            throw new BeanInitializationException("the property with key 'spring.application.name' must be set!");
        }
        SubThread subThread = new SubThread();
        subThread.start();
        WatcherThread watcherThread = new WatcherThread(subThread);
        watcherThread.start();
    }

    class SubThread extends Thread {
        boolean mistaken = false;
        @Override
        public void run() {
            Jedis jedis = null;
            try {
                jedis = new Jedis(redisLimiterProperties.getRedisHost(), redisLimiterProperties.getRedisPort(), 0);
                jedis.subscribe(RedisLimiterConfigProcessor.this, redisLimiterProperties.getChannel());
            }
            catch (JedisConnectionException e) {
                mistaken = true;
            }
            finally {
                if(jedis != null) {
                    jedis.close();
                }

            }
        }
        public boolean isMistaken() {
            return mistaken;
        }
    }

    class WatcherThread extends Thread {
        SubThread subThread;
        WatcherThread(SubThread subThread) {
            this.subThread = subThread;
        }
        public void run() {
            while(true) {
                try {
                    sleep(5000);
                }
                catch(InterruptedException e) {
                }
                if(subThread.isMistaken()) {
                    subThread = new SubThread();
                    subThread.start();
                }
            }
        }
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class clazz = bean.getClass();
        if(clazz.isAnnotationPresent(RestController.class) || clazz.isAnnotationPresent(Controller.class)) {
            Method[] methods = clazz.getDeclaredMethods();
             for (Method method : methods) {
                int modifiers = method.getModifiers();
                if(Modifier.isPublic(modifiers) && method.isAnnotationPresent(DynamicRateLimiter.class)) {
                    if(!redisLimiterProperties.isEnableDynamicalConf()) {
                        throw new RuntimeException("Must set spring.redisLimiter.enableDynamicalConf = true, then you can use DynamicRateLimiter annotation.");
                    }
                    DynamicRateLimiter dynamicRateLimiter = method.getAnnotation(DynamicRateLimiter.class);
                    int permits = dynamicRateLimiter.permits();
                    TimeUnit timeUnit = dynamicRateLimiter.timeUnit();
                    String path = dynamicRateLimiter.path();
                    String baseExp = dynamicRateLimiter.base();
                    LimiterConfig config = new LimiterConfig();
                    config.setApplicationName(applicationName);
                    config.setBaseExp(baseExp);
                    config.setPath(path);
                    config.setPermits(permits);
                    config.setTimeUnit(timeUnit.name());
                    config.setControllerName(clazz.getSimpleName());
                    config.setMethodName(method.getName());
                    String key = clazz.getSimpleName()+":"+method.getName();
                    if(configMap.containsKey(key)) {
                        throw new RuntimeException(String.format("Controller %s method %s has conflict.", clazz.getSimpleName(), method.getName()));
                    }
                    configMap.put(key, config);
                }
            }
        }
        return bean;
    }

    @Override
    public void onMessage(String channel, String message) {
        ObjectMapper objectMapper = new ObjectMapper();
        LimiterConfig config = null;
        try {
            config = objectMapper.readValue(message, LimiterConfig.class);
        }
        catch(IOException e) {
            logger.error("read config from message failed. the message content is " + message);
        }
        if(config != null) {
            if (applicationName.equals(config.getApplicationName())) {
                String key = config.getControllerName() + ":" + config.getMethodName();
                if (config.isDeleted()) {
                    configMap.remove(key);
                } else {
                    configMap.put(key, config);
                }
            }
        }
    }

    public LimiterConfig get(String key) {
        return configMap.get(key);
    }

}
