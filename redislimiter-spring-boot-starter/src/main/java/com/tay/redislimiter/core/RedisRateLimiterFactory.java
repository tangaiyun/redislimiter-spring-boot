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

import com.tay.redislimiter.core.RedisRateLimiter;
import lombok.RequiredArgsConstructor;
import redis.clients.jedis.JedisPool;

import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@RequiredArgsConstructor
public class RedisRateLimiterFactory {

    private final JedisPool jedisPool;
    private final WeakHashMap<String, RedisRateLimiter> limiterMap = new WeakHashMap<>();
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();


    public RedisRateLimiter get(String keyPrefix, TimeUnit timeUnit, int permits) {
        RedisRateLimiter redisRateLimiter = null;
        try {
            lock.readLock().lock();
            if(limiterMap.containsKey(keyPrefix)) {
                redisRateLimiter = limiterMap.get(keyPrefix);
            }
        }
        finally {
            lock.readLock().unlock();
        }

        if(redisRateLimiter == null) {
            try {
                lock.writeLock().lock();
                if(limiterMap.containsKey(keyPrefix)) {
                    redisRateLimiter = limiterMap.get(keyPrefix);
                }
                if(redisRateLimiter == null) {
                    redisRateLimiter = new RedisRateLimiter(jedisPool, timeUnit, permits);
                    limiterMap.put(keyPrefix, redisRateLimiter);
                }
            }
            finally {
                lock.writeLock().unlock();
            }
        }
        return redisRateLimiter;
    }
}
