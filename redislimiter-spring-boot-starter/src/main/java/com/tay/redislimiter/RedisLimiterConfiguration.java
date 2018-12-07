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
package com.tay.redislimiter;

import com.tay.redislimiter.core.RedisRateLimiterFactory;
import com.tay.redislimiter.dynamic.LimiterConfigResource;
import com.tay.redislimiter.dynamic.RedisLimiterConfigProcessor;
import com.tay.redislimiter.event.DefaultRateCheckFailureListener;
import com.tay.redislimiter.event.DefaultRateExceedingListener;
import com.tay.redislimiter.event.RateCheckFailureListener;
import com.tay.redislimiter.event.RateExceedingListener;
import com.tay.redislimiter.web.RateCheckInterceptor;
import com.tay.redislimiter.web.RateLimiterWebMvcConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;


@Configuration
@EnableConfigurationProperties(RedisLimiterProperties.class)
public class RedisLimiterConfiguration {

    @Autowired
    private RedisLimiterProperties redisLimiterProperties;

    @Bean
    @ConditionalOnMissingBean(JedisPool.class)
    public JedisPool jedisPool() {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxIdle(redisLimiterProperties.getRedisPoolMaxIdle());
        jedisPoolConfig.setMinIdle(redisLimiterProperties.getRedisPoolMinIdle());
        jedisPoolConfig.setMaxWaitMillis(redisLimiterProperties.getRedisPoolMaxWaitMillis());
        jedisPoolConfig.setMaxTotal(redisLimiterProperties.getRedisPoolMaxTotal());
        jedisPoolConfig.setTestOnBorrow(true);
        JedisPool jedisPool = new JedisPool(jedisPoolConfig, redisLimiterProperties.getRedisHost(), redisLimiterProperties.getRedisPort(), redisLimiterProperties.getRedisConnectionTimeout(), redisLimiterProperties.getRedisPassword());
        return jedisPool;
    }

    @Bean
    @ConditionalOnMissingBean(RedisRateLimiterFactory.class)
    public RedisRateLimiterFactory redisRateLimiterFactory() {
        RedisRateLimiterFactory redisRateLimiterFactory = new RedisRateLimiterFactory(jedisPool());
        return redisRateLimiterFactory;
    }

    @Bean
    @ConditionalOnMissingBean(RateCheckInterceptor.class)
    public RateCheckInterceptor rateCheckInterceptor() {
        RateCheckInterceptor rateCheckInterceptor;
        if (redisLimiterProperties.isEnableDynamicalConf()) {
            rateCheckInterceptor = new RateCheckInterceptor(redisLimiterProperties, rateCheckTaskRunner(), redisLimiterConfigProcessor());
        } else {
            rateCheckInterceptor = new RateCheckInterceptor(redisLimiterProperties, rateCheckTaskRunner(),null);
        }
        return rateCheckInterceptor;
    }

    @Bean
    @ConditionalOnMissingBean(RateLimiterWebMvcConfigurer.class)
    public RateLimiterWebMvcConfigurer rateLimiterWebMvcConfigurer() {
        RateLimiterWebMvcConfigurer rateLimiterWebMvcConfigurer = new RateLimiterWebMvcConfigurer(rateCheckInterceptor());
        return rateLimiterWebMvcConfigurer;
    }

    @Bean
    @ConditionalOnMissingBean(RateCheckTaskRunner.class)
    public RateCheckTaskRunner rateCheckTaskRunner() {
        RateCheckTaskRunner rateCheckTaskRunner = new RateCheckTaskRunner(redisRateLimiterFactory(), redisLimiterProperties);
        return rateCheckTaskRunner;
    }

    @Bean
    @ConditionalOnMissingBean(RateCheckFailureListener.class)
    public RateCheckFailureListener rateCheckFailureListener() {
        RateCheckFailureListener rateCheckFailureListener = new DefaultRateCheckFailureListener();
        return rateCheckFailureListener;
    }

    @Bean
    @ConditionalOnMissingBean(RateExceedingListener.class)
    public RateExceedingListener rateExceedingListener() {
        RateExceedingListener rateExceedingListener = new DefaultRateExceedingListener();
        return rateExceedingListener;
    }

    @Bean
    @ConditionalOnMissingBean(RedisLimiterConfigProcessor.class)
    @ConditionalOnProperty(prefix = "spring.redis-limiter", name = "enable-dynamical-conf", havingValue = "true")
    public RedisLimiterConfigProcessor redisLimiterConfigProcessor() {
        RedisLimiterConfigProcessor redisLimiterConfigProcessor = new RedisLimiterConfigProcessor(redisLimiterProperties);
        return redisLimiterConfigProcessor;
    }

    @Bean
    @ConditionalOnMissingBean(LimiterConfigResource.class)
    @ConditionalOnProperty(prefix = "spring.redis-limiter", name = "enable-dynamical-conf", havingValue = "true")
    public LimiterConfigResource limiterConfigResource() {
        LimiterConfigResource limiterConfigResource = new LimiterConfigResource(jedisPool(), redisLimiterProperties, redisLimiterConfigProcessor());
        return limiterConfigResource;
    }


}
