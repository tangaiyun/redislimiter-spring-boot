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
package com.tay.redislimiter.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import redis.clients.jedis.JedisPool;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class RedisRateLimiterFactory {

    private final JedisPool jedisPool;

    private Cache<CacheKey, RedisRateLimiter> redisRateLimiterCache =
            Caffeine.newBuilder().maximumSize(10000).expireAfterAccess(1, TimeUnit.HOURS).build();

    public RedisRateLimiter get(TimeUnit timeUnit, int permits) {
        CacheKey key = new CacheKey(timeUnit, permits);
        RedisRateLimiter redisRateLimiter = redisRateLimiterCache.getIfPresent(key);
        if(redisRateLimiter == null) {
            redisRateLimiter = new RedisRateLimiter(jedisPool, timeUnit, permits);
            redisRateLimiterCache.put(key, redisRateLimiter);
        }
        return redisRateLimiter;
    }
    @Data
    @AllArgsConstructor
    private static class CacheKey {
        private TimeUnit timeUnit;
        private int permits;
    }
}
