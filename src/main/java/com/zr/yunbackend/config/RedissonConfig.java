package com.zr.yunbackend.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class RedissonConfig {

    //单Redis节点模式
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://110.40.137.152:6379")
              .setTimeout(3000)
              .setPassword("151212")
              .setDatabase(0);
        return  Redisson.create(config);   //注意这里要返回redissonClient，和名称对应，因为@Resource会按名称找到bean注入
    }
}