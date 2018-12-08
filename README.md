[英文](https://github.com/tangaiyun/redislimiter-spring-boot/blob/master/README.md) | [中文](https://github.com/tangaiyun/redislimiter-spring-boot/blob/master/README-CN.md)

# redislimiter-spring-boot
a excellent API limiting framework for Spring boot/cloud application, especially for microservice project 

# Quickstart
## 1. git clone https://github.com/tangaiyun/redislimiter-spring-boot.git

## 2. cd redislimiter-spring-boot-starter

## 3. mvn clean install

## 4. create a Spring boot API project refer to sample project "demo1"，and you need to add dependency in pom.xml
```
        <dependency>
            <groupId>com.tay</groupId>
            <artifactId>redislimiter-spring-boot-starter</artifactId>
            <version>0.0.1-SNAPSHOT</version>
        </dependency>
```
## 5. modify application configuration---resources/application.yml
```
server:
    port: 8888                                #port
spring:
    application:
        name: demo1                           #application name must be set, otherwise the application can not start
    redis-limiter:                            #Limiter configuration
        redis-host: 127.0.0.1                 #redis server ip  
        check-action-timeout: 100             #check action will be executed asynchronous, this is the timeout value
        enable-dynamical-conf: true           #turn on the switch for dynammically limiting configuration support 
```
spring.application.name must be set

## 6. create a RestController class
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
    //limiting based on userid， and an unique user can visit twice per one minute，userid was contained in the http header
    //RateLimiter annotation that means this configuration can not be change in runtime
    @RateLimiter(base = "#Headers['userid']", permits = 2, timeUnit = TimeUnit.MINUTES) 
    public String test() {
        return "test!";
    }

    @GetMapping("/dynamictest")
    //limiting based on user's IP, and an unique IP can visit five times per one minute, so 'X-Real-IP' is key of header for IP 
    //DynamicRateLimiter annotation that means this configuration can be changed dynamically in runime
    @DynamicRateLimiter(base = "#Headers['X-Real-IP']", permits = 5, timeUnit = TimeUnit.MINUTES)
    public String dynamicTest() {
        return "dynamictest!";
    }

}
```
## 7. Start Redis server in local machine, if your PC has Docker, there is a easy way to start Redis server in Docker
```
sudo docker run -d -p 6379:6379 redis
```
it is so crazy!

## 8. Run Demo1Application.java

## 9. Testing
```
You can use a HTTP client tool such as postman,restd, curl, and visit the URL http://localhost:8888/demo/test. Don't forget to add a pair value "userid=tom" in your HTTP request header, then you can find the user with userid "tom" can visit this url twice successfully at most in one minute.

by postman,restd visit the URL http://localhost:8888/demo/dynamictest, and put the pair value "X-Real-IP=127.0.0.1" in your HTTP request header,  then you can find only five requests with header "X-Real-IP=127.0.0.1" will be successful in one minute.
```

# Advanced Guide
## 1. All Configuration Items
```
spring:
    redis-limiter: 
        redis-host: 127.0.0.1           # redis server IP                   default：127.0.0.1
        redis-port: 6379                # redis service port                default：6379  
        redis-password: test            # redis password                    default：null 
        redis-connection-timeout: 2000  # redis connection timeout          default：2000
        redis-pool-max-idle: 50         # redis pool max idle               default: 50
        redis-pool-min-idle: 10         # redis pool mim idle               default：10 
        redis-pool-max-wait-millis： -1 # max wait time for get connection  default：-1 
        redis-pool-max-total: 200       # max total connection              default：200
        redis-key-prefix: #RL           # key prefix for visit footprint    default: #RL
        check-action-timeout: 100       # check action execution timeout    default: 100
        enable-dynamical-conf: true     # the switch for enable dynamical   default：false 
        channel： #RLConfigChannel      # conf change event pub/sub channel default： #RLConfigChannel   
```
## 2 Annotation
@RateLimiter, @DynamicRateLimiter   

## 2.1 Annotation -- General Description
@RateLimiter @DynamicRateLimiter these two annotations have same four attributes (base, path, timeUnit, permits)

```
@Retention(RUNTIME)
@Target({ METHOD })
public @interface RateLimiter {

    String base() default "";

    String path() default "";

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    int permits() default 10000;
}

@Retention(RUNTIME)
@Target({ METHOD })
public @interface DynamicRateLimiter {
    String base() default "";

    String path() default "";

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    int permits() default 10000;
}
```


## 2.2 Annotation -- base(Spel expression)
Two annotations have the "base" attribute，that means what your limiting based on(maybe user's id, remote IP etc.)，and if you don't asssign "base" attribute, then all requests will be accumulated as a whole one，the "base" should be a Spel exression。

```
@RateLimiter(base = "#Headers['userid']", permits = 2, timeUnit = TimeUnit.MINUTES) 
@DynamicRateLimiter(base = "#Headers['X-Real-IP']", permits = 5, timeUnit = TimeUnit.MINUTES)
```
at the present, the "base" expression only supports get value from HTTP header and cookie. In Spel expression evaluation context they are named as "Headers" and "Cookies" separately, so the two expressions are valid as below:
```
"#Headers['X-Real-IP']"
"#Cookies['userid']"
```
## 2.3 Annotation -- path
The path has default value "" if you did not set. When the path has value "", and the value of path will be set as request.getRequestURI(). In general, that is OK. But in one special case, you should need to set path explicitly.

for example as below:
```
    @GetMapping("/user/{userid}")
    @DynamicRateLimiter(base = "#Headers['X-Real-IP']", path = "/user", permits = 5, timeUnit = TimeUnit.MINUTES)
    public User get(@PathVariable String userid) {
        User user ...
       
        return user;
    }
```
the GetMapping has PathVariable-{userid}. In this case, we would not count visiting times base on "/user/001". If we set the path
value to "/user", so all requsts like "/user/xxx" will be added up base on "/user".  


## 2.4 Annotation -- timeUnit
Four TimeUnits are valid ：
```
TimeUnit.SECONDS, TimeUnit.MINUTES, TimeUnit.HOURS, TimeUnit.DAYS
```

## 2.5 Annotation -- permits

Number of visits allowed per unit of time

## 3. Dynamic configuration
@DynamicRateLimiter annotation makes configuration can be changed dynamically, we can change the configuraton by internal RESTful API.
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
Currently, this framework support three actions (update, query, delete).

for example demo1 project

GET http://localhost:8888/limiterconfig?controller=DemoController&method=dynamicTest, the result will be:

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
 if we want to update configuration, assign Content-Type as application/json, then excute  PUT http://localhost:8888/limiterconfig, the request body as below: 
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


if we want to delete a configuration, execute DELETE http://localhost:8888/limiterconfig?controller=DemoController&method=dynamicTest, the limiting configuration item for Controller "DemoController" and method "dynamicTest" will be deleted.




