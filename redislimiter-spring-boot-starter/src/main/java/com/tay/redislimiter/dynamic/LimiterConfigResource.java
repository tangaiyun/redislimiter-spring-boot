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
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


@RestController
@RequestMapping("/limiterconfig")
@RequiredArgsConstructor
public final class LimiterConfigResource implements InitializingBean, ApplicationContextAware {

    private static Logger logger = LoggerFactory.getLogger(RedisLimiterConfigProcessor.class);

    private final JedisPool jedisPool;

    private final RedisLimiterProperties redisLimiterProperties;

    private final RedisLimiterConfigProcessor redisLimiterConfigProcessor;

    private ApplicationContext applicationContext;

    private String applicationName;

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet(){
        applicationName = applicationContext.getEnvironment().getProperty("spring.application.name");
        if(applicationName == null) {
            throw new BeanInitializationException("the property with key 'spring.application.name' must be set!");
        }

    }

    @PutMapping
    public void update(@RequestBody LimiterConfig limiterConfig, HttpServletResponse response) throws IOException {
        if(applicationName.equals(limiterConfig.getApplicationName())) {
            publish(limiterConfig);
        }
        else {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.getWriter().print("Bad request for updating limiter configuration!");
        }
    }
    @GetMapping
    public LimiterConfig get(@RequestParam("controller") String controller, @RequestParam("method")String method) {
        String limiterConfigKey = controller + ":" + method;
        return redisLimiterConfigProcessor.get(limiterConfigKey);
    }

    @DeleteMapping
    public void delete(@RequestParam("controller") String controller, @RequestParam("method")String method) {
        LimiterConfig limiterConfig = new LimiterConfig();
        limiterConfig.setApplicationName(applicationName);
        limiterConfig.setControllerName(controller);
        limiterConfig.setMethodName(method);
        limiterConfig.setDeleted(true);
        publish(limiterConfig);
    }

    private void publish(LimiterConfig limiterConfig) {
        ObjectMapper objectMapper = new ObjectMapper();
        String configMessage = null;
        try {
            configMessage = objectMapper.writeValueAsString(limiterConfig);
        }
        catch(IOException e) {
            logger.error("convert LimiterConfig object to json failed.");
        }
        Jedis jedis = jedisPool.getResource();
        jedis.publish(redisLimiterProperties.getChannel(), configMessage);
    }
}
