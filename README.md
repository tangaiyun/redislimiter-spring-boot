# redislimiter-spring-boot
一个优秀的spring boot API限流框架

#快速开始
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
