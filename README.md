# redislimiter-spring-boot
一个优秀的spring boot API限流框架

# 快速开始
## 1. git clone https://github.com/tangaiyun/redislimiter-spring-boot.git

## 2. cd redislimiter-spring-boot-starter

## 3. mvn clean install

## 4. 新建一个Spring boot API 项目，具体参考demo1项目，要在项目依赖中加入
```
        <dependency>
            <groupId>com.tay</groupId>
            <artifactId>redislimiter-spring-boot-starter</artifactId>
            <version>0.0.1-SNAPSHOT</version>
        </dependency>
```
## 5. 修改项目resources/application.yml文件
```
server:
    port: 8888
spring:
    application:
        name: demo1                         
    redis-limiter:
        redis-host: 127.0.0.1
        check-action-timeout: 100
        enable-dynamical-conf: true
```
spring.application.name必须配置

## 6. 新建一个RestController类
```
package com.tay.demo1;

import com.tay.redislimiter.RateLimiter;
import com.tay.redislimiter.dynamic.DynamicRateLimiter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;


@RestController
@RequestMapping("/demo")
public class DemoController {

    @GetMapping("/test")
    //基于用户限流，独立用户每分钟最多2次访问，用户id在header中，key为userid
    //RateLimiter标签为静态配置，此类配置不可动态修改
    @RateLimiter(base = "#Headers['userid']", permits = 2, timeUnit = TimeUnit.MINUTES) 
    public String test() {
        return "test!";
    }

    @GetMapping("/dynamictest")
    //基于来源ip限流，独立ip每分钟最多访问5次访问，来源ip位于header中，key为X-Real-IP
    //DynamicRateLimiter标签代表动态配置，此类配置可在运行时动态修改
    @DynamicRateLimiter(base = "#Headers['X-Real-IP']", permits = 5, timeUnit = TimeUnit.MINUTES)
    public String dynamicTest() {
        return "dynamictest!";
    }

}
```

## 7. 运行Demo1Application.java

## 8. 测试
### 通过postman或者restd访问url http://localhost:8888/demo/test 在header中指定userid=tom, 可以发现tom一分钟最多只能访问2次
### 通过postman或者restd访问url http://localhost:8888/demo/dynamictest 在header中指定X-Real-IP=127.0.0.1, 可以发现127.0.0.1一分钟最多只能访问5次

# 高阶教程
## 1. 配置项大全
```
spring:
    redis-limiter: 
        redis-host: 127.0.0.1           # redis server IP                  默认值：127.0.0.1
        redis-port: 6379                # redis service 端口               默认值：6379  
        redis-password: test            # redis 访问密码                   默认值：null 
        redis-connection-timeout: 2000  # redis 连接超时时间               默认值：2000
        redis-pool-max-idle: 50         # redis 连接池最大空闲连接数        默认值：50
        redis-pool-min-idle: 10         # redis 连接池最小空闲连接数        默认值： 10 
        redis-pool-max-wait-millis： -1 # 从连接池中获取连接最大等待时间     默认值： -1 
        redis-pool-max-total: 200       # 连接池中最大连接数                默认值： 200
        redis-key-prefix: #RL           # 访问痕迹key值前缀                 默认值： #RL
        check-action-timeout: 100       # 访问检查动作最大执行时间(单位毫秒) 默认值： 100
        enable-dynamical-conf: true     # 是否开启动态配置                  默认值： false 
        channel： #RLConfigChannel      # 配置变更事件发送channel名称        默认值： #RLConfigChannel   
```

## 2. base表达式说明
@RateLimiter @DynamicRateLimiter 这两个表达式用法完全一致，它们都有一个属性base，含义就是限流是"基于what"来进行的。

```
@RateLimiter(base = "#Headers['userid']", permits = 2, timeUnit = TimeUnit.MINUTES) 
@DynamicRateLimiter(base = "#Headers['X-Real-IP']", permits = 5, timeUnit = TimeUnit.MINUTES)
```

