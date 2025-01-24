package com.zr.yunbackend.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
public class RedisTokenUtils {
    //--整合redis---
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final String TOKEN_KEY_PREFIX = "JWT token:";

    // 将Token存入Redis，使用jti作为key
    public void saveToken(String jti, String token, long expireTime) {
        stringRedisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + jti, token, expireTime, TimeUnit.MILLISECONDS);
    }

    //从Redis获取Token
    public String getToken(String jti) {
        return stringRedisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + jti);
    }

    //从Redis删除Token
    public void deleteToken(String jti) {
        stringRedisTemplate.delete(TOKEN_KEY_PREFIX + jti);
    }
}
