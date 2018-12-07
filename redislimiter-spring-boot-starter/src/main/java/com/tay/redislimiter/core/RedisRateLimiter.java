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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Redis based Rate limiter
 *
 * @author Aiyun Tang <aiyun.tang@gmail.com>
 */
@RequiredArgsConstructor
public class RedisRateLimiter {
    private final JedisPool jedisPool;
    private final TimeUnit timeUnit;
    private final int permitsPerUnit;
    private static final String LUA_SECOND_SCRIPT = " local current; "
            + " current = redis.call('incr',KEYS[1]); "
            + " if tonumber(current) == 1 then "
            + " 	redis.call('expire',KEYS[1],ARGV[1]); "
            + "     return 1; "
            + " else"
            + " 	if tonumber(current) <= tonumber(ARGV[2]) then "
            + "     	return 1; "
            + "		else "
            + "			return -1; "
            + "     end "
            + " end ";
    private static final String LUA_PERIOD_SCRIPT =   " local currentSectionCount;"
            + " local previousSectionCount;"
            + " local totalCountInPeriod;"
            + " currentSectionCount = redis.call('zcount', KEYS[2], '-inf', '+inf');"
            + " previousSectionCount = redis.call('zcount', KEYS[1], ARGV[3], '+inf');"
            + " totalCountInPeriod = tonumber(currentSectionCount)+tonumber(previousSectionCount);"
            + " if totalCountInPeriod < tonumber(ARGV[5]) then "
            + " 	redis.call('zadd',KEYS[2],ARGV[1],ARGV[2]);"
            + "		if tonumber(currentSectionCount) == 0 then "
            + "			redis.call('expire',KEYS[2],ARGV[4]); "
            + "		end "
            + "     return 1"
            + "	else "
            + " 	return -1"
            + " end ";

    private static final int PERIOD_SECOND_TTL = 10;
    private static final int PERIOD_MINUTE_TTL = 2 * 60 + 10;
    private static final int PERIOD_HOUR_TTL = 2 * 3600 + 10;
    private static final int PERIOD_DAY_TTL = 2 * 3600 * 24 + 10;

    private static final long MICROSECONDS_IN_MINUTE = 60 * 1000000;
    private static final long MICROSECONDS_IN_HOUR = 3600 * 1000000;
    private static final long MICROSECONDS_IN_DAY = 24 * 3600 * 1000000;


    public boolean acquire(String keyPrefix){
        boolean rtv = false;
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                if (timeUnit == TimeUnit.SECONDS) {
                    String keyName = getKeyNameForSecond(jedis, keyPrefix);

                    List<String> keys = new ArrayList<>(1);
                    keys.add(keyName);
                    List<String> args = new ArrayList<>(2);
                    args.add(String.valueOf(getExpire()));
                    args.add(String.valueOf(permitsPerUnit));
                    Long val = (Long) jedis.eval(LUA_SECOND_SCRIPT, keys, args);
                    rtv = (val > 0);

                } else if (timeUnit == TimeUnit.MINUTES) {
                    rtv = doPeriod(jedis, keyPrefix);
                } else if (timeUnit == TimeUnit.HOURS) {
                    rtv = doPeriod(jedis, keyPrefix);
                } else if (timeUnit == TimeUnit.DAYS) {
                    rtv = doPeriod(jedis, keyPrefix);
                }
            }
        }
        return rtv;
    }
    private boolean doPeriod(Jedis jedis, String keyPrefix) {
        String[] keyNames = getKeyNames(jedis, keyPrefix);
        long currentTimeInMicroSecond = getRedisTime(jedis);
        String previousSectionBeginScore = String.valueOf((currentTimeInMicroSecond - getPeriodMicrosecond()));
        String expires =String.valueOf(getExpire());
        String currentTimeInMicroSecondStr = String.valueOf(currentTimeInMicroSecond);
        List<String> keys = new ArrayList<String>(2);
        keys.add(keyNames[0]);
        keys.add(keyNames[1]);
        List<String> args = new ArrayList<>(5);
        args.add(currentTimeInMicroSecondStr);
        args.add(currentTimeInMicroSecondStr);
        args.add(previousSectionBeginScore);
        args.add(expires);
        args.add(String.valueOf(permitsPerUnit));
        Long val = (Long)jedis.eval(LUA_PERIOD_SCRIPT, keys, args);
        return (val > 0);
    }

    /**
     *  因为redis访问实际上是单线程的，而且jedis.time()方法返回的时间精度为微秒级，每一个jedis.time()调用耗时应该会超过1微秒，因此我们可以认为每次jedis.time()返回的时间都是唯一且递增的
     */
    private long getRedisTime(Jedis jedis) {
        List<String> jedisTime = jedis.time();
        Long currentSecond = Long.parseLong(jedisTime.get(0));
        Long microSecondsElapseInCurrentSecond = Long.parseLong(jedisTime.get(1));
        Long currentTimeInMicroSecond = currentSecond * 1000000 + microSecondsElapseInCurrentSecond;
        return currentTimeInMicroSecond;
    }

    private String getKeyNameForSecond(Jedis jedis, String keyPrefix) {
        String keyName  = keyPrefix + ":" + jedis.time().get(0);
        return keyName;
    }

    private String[] getKeyNames(Jedis jedis, String keyPrefix) {
        String[] keyNames = null;
        if (timeUnit == TimeUnit.MINUTES) {
            long index = Long.parseLong(jedis.time().get(0)) / 60;
            String keyName1 = keyPrefix + ":" + (index - 1);
            String keyName2 = keyPrefix + ":" + index;
            keyNames = new String[] { keyName1, keyName2 };
        } else if (timeUnit == TimeUnit.HOURS) {
            long index = Long.parseLong(jedis.time().get(0)) / 3600;
            String keyName1 = keyPrefix + ":" + (index - 1);
            String keyName2 = keyPrefix + ":" + index;
            keyNames = new String[] { keyName1, keyName2 };
        } else if (timeUnit == TimeUnit.DAYS) {
            long index = Long.parseLong(jedis.time().get(0)) / (3600 * 24);
            String keyName1 = keyPrefix + ":" + (index - 1);
            String keyName2 = keyPrefix + ":" + index;
            keyNames = new String[] { keyName1, keyName2 };
        } else {
            throw new java.lang.IllegalArgumentException("Don't support this TimeUnit: " + timeUnit);
        }
        return keyNames;
    }

    private int getExpire() {
        int expire = 0;
        if (timeUnit == TimeUnit.SECONDS) {
            expire = PERIOD_SECOND_TTL;
        } else if (timeUnit == TimeUnit.MINUTES) {
            expire = PERIOD_MINUTE_TTL;
        } else if (timeUnit == TimeUnit.HOURS) {
            expire = PERIOD_HOUR_TTL;
        } else if (timeUnit == TimeUnit.DAYS) {
            expire = PERIOD_DAY_TTL;
        } else {
            throw new java.lang.IllegalArgumentException("Don't support this TimeUnit: " + timeUnit);
        }
        return expire;
    }

    private long getPeriodMicrosecond() {
        if (timeUnit == TimeUnit.MINUTES) {
            return MICROSECONDS_IN_MINUTE;
        } else if (timeUnit == TimeUnit.HOURS) {
            return MICROSECONDS_IN_HOUR;
        } else if (timeUnit == TimeUnit.DAYS) {
            return MICROSECONDS_IN_DAY;
        } else {
            throw new java.lang.IllegalArgumentException("Don't support this TimeUnit: " + timeUnit);
        }
    }

}

