package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import com.hmdp.service.ILock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author haowe
 */
public class SimpleRedisLock implements ILock {


    private String name;
    private StringRedisTemplate redisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }


    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    @Override
    public boolean tryLock(long timeoutSec) {
        //获取当前线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        Boolean success = redisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + name,
                threadId,
                timeoutSec,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
//        //获取锁的标识
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        String id = redisTemplate.opsForValue().get(KEY_PREFIX + name);
//        //如果为当前线程所持有id则释放
//        if (threadId.equals(id)) {
//            redisTemplate.delete(KEY_PREFIX + name);
//        }

        //这里可能会发生STW 也就是垃圾回收器进行垃圾回收
        redisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                Collections.singletonList(ID_PREFIX + Thread.currentThread().getId()));
    }


}
