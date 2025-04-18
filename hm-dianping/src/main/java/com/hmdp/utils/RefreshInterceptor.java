package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author haowe
 */


public class RefreshInterceptor implements HandlerInterceptor {

    private StringRedisTemplate redisTemplate;


    public RefreshInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取token
        String token = request.getHeader("authorization");
        if (StringUtils.isEmpty(token)) {
            return true;
        }
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = redisTemplate.opsForHash().entries(tokenKey);
        if (userMap.isEmpty()) {
            return true;
        }
        User user = BeanUtil.fillBeanWithMap(userMap, new User(), false);
        //保存到当前线程的threadLocal里面里面
        UserHolder.saveUser((User) user);
        //刷新key的ttl
        redisTemplate.expire(tokenKey, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //完成执行后 移除该threaLocal避免内存泄露
        UserHolder.removeUser();
    }
}
