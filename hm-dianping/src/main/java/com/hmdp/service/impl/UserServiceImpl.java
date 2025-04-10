package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //第一步校验手机号
        if (!RegexUtils.isPhoneInvalid(phone)) {
            //返回错误信息
            return Result.fail("手机号验证码错误");
        }
        //符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //保存验证码到session
        session.setAttribute("code", code);

        //发送验证码  通过第三方平台实现
        log.debug("发送短信验证码成功,验证码:" + code);

        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //再次校验手机号和验证码
        String phone = loginForm.getPhone();
        if (!RegexUtils.isPhoneInvalid(phone)) {
            //返回错误信息
            return Result.fail("手机号验证码错误");
        }

        //校验验证码
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            //不一致报错
            return Result.fail("验证码错误");
        }

        //一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //判断用户是否存在
        if (user == null) {
            //不存在创建新用户并保存,创建后并返给我
            user = createUserWithPhone(phone);
        }
        // 保存用户信息到session中
        session.setAttribute("user", user);


        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        //代码看着优专
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        save(user);
        return user;
    }
}
