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

import com.tay.redislimiter.core.RedisRateLimiter;
import com.tay.redislimiter.core.RedisRateLimiterFactory;
import com.tay.redislimiter.event.RateCheckFailureEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.concurrent.*;

@RequiredArgsConstructor
public class RateCheckTaskRunner implements ApplicationContextAware {
    private ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private final RedisRateLimiterFactory redisRateLimiterFactory;

    private final RedisLimiterProperties redisLimiterProperties;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public boolean checkRun(String rateLimiterKey, TimeUnit timeUnit, int permits) {
        CheckTask task = new CheckTask(rateLimiterKey, timeUnit, permits);
        Future<Boolean> checkResult = executorService.submit(task);
        boolean retVal = true;
        try {
            retVal = checkResult.get(redisLimiterProperties.getCheckActionTimeout(), TimeUnit.MILLISECONDS);
        }
        catch(Exception e) {
            applicationContext.publishEvent(new RateCheckFailureEvent(e, "Access rate check task executed failed."));
        }
        return retVal;
    }

    class CheckTask implements Callable<Boolean> {
        private String rateLimiterKey;
        private TimeUnit timeUnit;
        private int permits;
        CheckTask(String rateLimiterKey, TimeUnit timeUnit, int permits) {
            this.rateLimiterKey = rateLimiterKey;
            this.timeUnit = timeUnit;
            this.permits = permits;
        }
        public Boolean call() {
            RedisRateLimiter redisRatelimiter = redisRateLimiterFactory.get(timeUnit, permits);
            return redisRatelimiter.acquire(rateLimiterKey);
        }
    }
}
