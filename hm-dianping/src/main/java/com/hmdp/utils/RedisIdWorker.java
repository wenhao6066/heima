package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author haowe
 */
@Component
public class RedisIdWorker {


    //开始的一个时间戳
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    private final StringRedisTemplate redisTemplate;

    public RedisIdWorker(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    public long nextId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        //当前时间戳
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;
        //2.生成序列号
        //2.获取到当前日期
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        //自增长
        long count = redisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //3.拼接并返回
        return timeStamp << 32 | count;
    }


}
