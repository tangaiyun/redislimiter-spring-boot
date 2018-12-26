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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.redis-limiter")
@Data
public final class RedisLimiterProperties {
    /**
     * Redis server host
     */
    private String redisHost = "127.0.0.1";
    /**
     * Redis service port
     */
    private int redisPort = 6379;
    /**
     * Redis access password
     */
    private String redisPassword = null;
    /**
     * Redis connection timeout
     */
    private int redisConnectionTimeout = 2000;
    /**
     * max idle connections in the pool
     */
    private int redisPoolMaxIdle = 50;
    /**
     * min idle connection in the pool
     */
    private int redisPoolMinIdle = 10;
    /**
     * the max wait milliseconds for borrowing an instance from the pool
     */
    private long redisPoolMaxWaitMillis = -1;
    /**
     * the max total instances in the pool
     */
    private int redisPoolMaxTotal = 200;
    /**
     * the redis key prefix
     */
    private String redisKeyPrefix = "#RL";

    /**
     * check action execution timeout(MILLISECONDS)
     */
    private int checkActionTimeout = 100;

    /**
     * the flag to tell whether rate limiter configuration can change dynamically
     */
    private boolean enableDynamicalConf = false;

    /**
     * channel for pub/sub limiter configuration change event
     */
    private String channel = "#RLConfigChannel";
}
