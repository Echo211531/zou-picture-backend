package com.zr.yunbackend.manager;

import com.zr.yunbackend.exception.ErrorCode;
import com.zr.yunbackend.exception.ThrowUtils;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class RedisLimiterManager {
    @Resource
    private RedissonClient redissonClient;
    /**
     * 限流操作
     * @param key 区分不同的限流器，比如不同的用户 id 应该分别统计
     */
    public void doRateLimit(String key) {
        // 创建一个限流器
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        // 每分钟最多访问 3 次
        // 参数1 type：限流类型，可以是自定义的任何类型，用于区分不同的限流策略 OVERALL表示服务器一起统计
        // 参数2 rate：限流速率，即单位时间内允许通过的请求数量。
        // 参数3 rateInterval：限流时间间隔，即限流速率的计算周期长度。
        // 参数4 unit：限流时间间隔单位，可以是秒、毫秒等。     
        rateLimiter.trySetRate(RateType.OVERALL, 3, 1, RateIntervalUnit.MINUTES);
        // 每当一个操作来了后，请求一个令牌
        //这里1个令牌相当于访问1次，也可以设置为2，即一个用户一次占用两个请求
        //举个例子：设置会员请求耗1个令牌，也就是每秒可以访问5次
        boolean canOp = rateLimiter.tryAcquire(1);   //返回为true 则拿到令牌，反之
        ThrowUtils.throwIf(!canOp, ErrorCode.SYSTEM_ERROR);
    }
}