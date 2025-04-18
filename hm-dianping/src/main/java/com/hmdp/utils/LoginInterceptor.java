package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author haowe
 */


public class LoginInterceptor implements HandlerInterceptor {


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断是否需要拦截
        if(Objects.isNull(UserHolder.getUser())){
            //代表没有 需要拦截
            response.setStatus(404);
            return false;
        }
        //有用户则放行
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //完成执行后 移除该threaLocal避免内存泄露
        UserHolder.removeUser();
    }
}
