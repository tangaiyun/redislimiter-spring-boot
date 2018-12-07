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
package com.tay.redislimiter.web;

import com.tay.redislimiter.RateCheckTaskRunner;
import com.tay.redislimiter.RateLimiter;
import com.tay.redislimiter.RedisLimiterProperties;
import com.tay.redislimiter.dynamic.DynamicRateLimiter;
import com.tay.redislimiter.dynamic.LimiterConfig;
import com.tay.redislimiter.dynamic.RedisLimiterConfigProcessor;
import com.tay.redislimiter.event.RateExceedingEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class RateCheckInterceptor implements HandlerInterceptor, ApplicationContextAware, InitializingBean {

    private final RedisLimiterProperties redisLimiterProperties;

    private final RateCheckTaskRunner rateCheckTaskRunner;

    private final RedisLimiterConfigProcessor redisLimiterConfigProcessor;

    private ApplicationContext applicationContext;

    private String applicationName;

    @Override
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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        boolean isSuccess = true;
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        if (method.isAnnotationPresent(RateLimiter.class)) {
            isSuccess = handleStatic(method, request, response);
        }
        else if(method.isAnnotationPresent(DynamicRateLimiter.class)) {
            isSuccess = handleDynamic(method, request, response);
        }
        return isSuccess;
    }

    private boolean handleStatic(Method method, HttpServletRequest request, HttpServletResponse response) throws Exception{
        RateLimiter rateLimiterAnnotation = method.getAnnotation(RateLimiter.class);
        int permits = rateLimiterAnnotation.permits();
        TimeUnit timeUnit = rateLimiterAnnotation.timeUnit();
        String path = rateLimiterAnnotation.path();
        if ("".equals(path)) {
            path = request.getRequestURI();
        }

        String baseExp = rateLimiterAnnotation.base();
        String baseVal = "";
        if(!"".equals(baseExp)) {
            baseVal = eval(baseExp, request);
        }
        String rateLimiterKey = redisLimiterProperties.getRedisKeyPrefix() + ":" + applicationName + ":" + path + ":" + baseVal;
        boolean isSuccess = rateCheckTaskRunner.checkRun(rateLimiterKey, timeUnit, permits);

        if(!isSuccess) {
            rateExceeded(method, response, baseExp, baseVal, path, permits, timeUnit.name());
        }
        return isSuccess;
    }

    private boolean handleDynamic(Method method, HttpServletRequest request, HttpServletResponse response) throws Exception {
        boolean isSuccess = true;
        String limiterConfigKey = method.getDeclaringClass().getSimpleName() + ":" + method.getName();
        LimiterConfig limiterConfig = redisLimiterConfigProcessor.get(limiterConfigKey);
        if(limiterConfig != null) {
            String baseExp = limiterConfig.getBaseExp();
            String baseVal = "";
            if(!"".equals(baseExp)) {
                baseVal = eval(baseExp, request);
            }
            String path = limiterConfig.getPath();
            if("".equals(path)) {
                path = request.getRequestURI();
            }
            int permits = limiterConfig.getPermits();
            String timeUnit = limiterConfig.getTimeUnit();
            String rateLimiterKey = redisLimiterProperties.getRedisKeyPrefix() + ":" + applicationName + ":" + path + ":" + baseVal;
            isSuccess = rateCheckTaskRunner.checkRun(rateLimiterKey, TimeUnit.valueOf(timeUnit), permits);
            if(!isSuccess) {
                rateExceeded(method, response, baseExp, baseVal, path, permits, timeUnit);
            }
        }
        return isSuccess;
    }

    private void rateExceeded(Method method, HttpServletResponse response, String baseExp, String baseVal, String path, int permits, String timeUnit) throws Exception {
        buildDenyResponse(response);
        RateExceedingEvent rateExceedingEvent = new RateExceedingEvent();
        rateExceedingEvent.setApplicationName(applicationName);
        rateExceedingEvent.setControllerName(method.getDeclaringClass().getSimpleName());
        rateExceedingEvent.setMethodName(method.getName());
        rateExceedingEvent.setBaseExp(baseExp);
        rateExceedingEvent.setBaseValue(baseVal);
        rateExceedingEvent.setPath(path);
        rateExceedingEvent.setPermits(permits);
        rateExceedingEvent.setTimeUnit(timeUnit);
        applicationContext.publishEvent(rateExceedingEvent);
    }

    private String eval(String baseExp, HttpServletRequest request) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        ExpressionParser expressionParser = new SpelExpressionParser();
        mountCookies(request, context);
        mountHeaders(request, context);
        Expression expression = expressionParser.parseExpression(baseExp);
        String baseVal = expression.getValue(context, String.class);
        if(baseVal == null) {
            baseVal = "";
        }
        return baseVal;
    }

    private void mountCookies(HttpServletRequest request, StandardEvaluationContext context) {
        HashMap<String, String> cookieMap = new HashMap<>();
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                cookieMap.put(cookie.getName(), cookie.getValue());
            }
        }
        context.setVariable("Cookies", cookieMap);
    }

    private void mountHeaders(HttpServletRequest request, StandardEvaluationContext context) {
        HashMap<String, String> headerMap = new HashMap();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                headerMap.put(headerName, request.getHeader(headerName));
            }
        }
        context.setVariable("Headers", headerMap);
    }

    private void buildDenyResponse(HttpServletResponse response) throws Exception{
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.getWriter().print("Access denied because of exceeding access rate");
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    }

}
