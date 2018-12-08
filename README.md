# redislimiter-spring-boot
一个优秀的分布式spring boot/Spring Cloud API限流框架，特别适合微服务架构.

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
    port: 8888                                #端口
spring:
    application:
        name: demo1                           #应用名称必须要配置，不然无法启动
    redis-limiter:                            #限流器配置
        redis-host: 127.0.0.1                 #redis server ip  
        check-action-timeout: 100             #访问检查动作最大执行时间(单位毫秒）
        enable-dynamical-conf: true           #开启动态限流配置 
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
## 7. 在本机安装redis并启动，强烈建议在本机安装docker环境，然后执行
```
sudo docker run -d -p 6379:6379 redis
```
就是这么爽气！

## 8. 运行Demo1Application.java

## 9. 测试
```
通过postman或者restd访问url http://localhost:8888/demo/test 在header中指定userid=tom, 可以发现tom一分钟最多只能访问2次

通过postman或者restd访问url http://localhost:8888/demo/dynamictest 在header中指定X-Real-IP=127.0.0.1, 可以发现127.0.0.1一分钟最多只能访问5次
```

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
## 2 标签
@RateLimiter, @DynamicRateLimiter 是用户最经常使用到的。

## 2.1 标签说明 --整体说明
@RateLimiter @DynamicRateLimiter 这两个标签用法完全一致,他们都有4个属性base、path、timeUnit、permits.

```
@Retention(RUNTIME)
@Target({ TYPE, METHOD })
public @interface RateLimiter {

    String base() default "";

    String path() default "";

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    int permits() default 10000;
}

@Retention(RUNTIME)
@Target({ TYPE, METHOD })
public @interface DynamicRateLimiter {
    String base() default "";

    String path() default "";

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    int permits() default 10000;
}
```


## 2.2 标签说明 -- base参数(Spel表达式)说明
标签都有一个属性base，含义就是限流是"基于what"来进行的，如果你不指定base,那么所有的请求都会聚合在一起统计，base为一个Spel表达式。

```
@RateLimiter(base = "#Headers['userid']", permits = 2, timeUnit = TimeUnit.MINUTES) 
@DynamicRateLimiter(base = "#Headers['X-Real-IP']", permits = 5, timeUnit = TimeUnit.MINUTES)
```
目前base表达式仅支持从header和cookie中取值，Headers和Cookies就是两个Map, 下面两种配置都是合法的。
### "#Headers['X-Real-IP']"
### "#Cookies['userid']"

## 2.3 标签使用 -- path 参数说明
path 如果不设置默认值是"", 当path为"", 框架内部会把它改写为request.getRequestURI(),一般情况下框架默认行为就OK了。但在一种情况下你可能需要设置path参数，就是RequestMapping的path里面包含Path Parameters的情况，例如：
```
    @GetMapping("/user/{userid}")
    @DynamicRateLimiter(base = "#Headers['X-Real-IP']", path = "/user", permits = 5, timeUnit = TimeUnit.MINUTES)
    public User get(@PathVariable String userid) {
        User user ...
       
        return user;
    }
```
在这种情况下，我们一般不会基于"/user/001"这样统计，所有访问"/user/001", "/user/002"的请求都会聚合到path "/user'上统计。

## 2.4 标签使用 -- timeUnit 参数说明
访问统计时间单位，以下4种都是有效的：
```
TimeUnit.SECONDS, TimeUnit.MINUTES, TimeUnit.HOURS, TimeUnit.DAYS
```

## 2.5 标签使用 -- permits 参数说明
单位时间内允许访问的次数

## 3. 动态配置
动态配置使用@DynamicRateLimiter标签，动态配置含义就是在运行时可以动态修改限流配置，这个是通过提供内置配置访问Rest API来实现的。
```
RestController
@RequestMapping("/limiterconfig")
@RequiredArgsConstructor
public final class LimiterConfigResource implements InitializingBean, ApplicationContextAware {
    ...
    
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

```
目前提供了修改(PUT), 查询 (GET), 删除(DELETE)三种操作。

对于demo1项目

我们可以通过 GET http://localhost:8888/limiterconfig?controller=DemoController&method=dynamicTest 来获取限流配置，返回值为

```
{
  "applicationName": "demo1",
  "controllerName": "DemoController",
  "methodName": "dynamicTest",
  "baseExp": "#Headers['userid']",
  "path": "",
  "timeUnit": "MINUTES",
  "permits": 5,
  "deleted": false
}
```
 通过指定Content-Type为application/json  PUT http://localhost:8888/limiterconfig 来改动限流配置, 发送内容如
```
{
  "applicationName": "demo1",
  "controllerName": "DemoController",
  "methodName": "dynamicTest",
  "baseExp": "#Headers['userid']",
  "path": "",
  "timeUnit": "MINUTES",
  "permits": 10,
  "deleted": false
}

```


通过 DELETE http://localhost:8888/limiterconfig?controller=DemoController&method=dynamicTest 可删除限流配置




