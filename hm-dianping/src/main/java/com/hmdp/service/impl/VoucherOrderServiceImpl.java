package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) {

        //1.查询优惠卷
        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始 || 是否已经结束
        LocalDateTime beginTime = voucher.getBeginTime();
        LocalDateTime endTime = voucher.getEndTime();
        if (beginTime.isAfter(LocalDateTime.now())) {
            //开始时间在当前时间之后，还没有开始
            return Result.fail("秒杀还没开始");
        }
        if (endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        //3.判断库存是否充足
        if (voucher.getStock() < 1) {
            //库存不足
            return Result.fail("库存不足够！");
        }

        //以用户id作为锁，进行范围锁 而不是全锁
        Long userId = UserHolder.getUser().getId();
        //此时事务不存在，因为调用的不是Spring管理返回的代理对象，此时需要调用Spring管理的代理对象
        //此时这样调用的service才会具有事务
//        synchronized (userId.toString().intern()) {
//            IVoucherOrderService voucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
//            return voucherOrderService.createOrder(voucherId);
//        }
//        SimpleRedisLock lock = new SimpleRedisLock("order::" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        //反向来写最好，最好不要写嵌套
        if (!isLock) {
            //获取失败
            return Result.fail("一个人只能下一单");
        }
        //说明获取锁成功了
        try {
            IVoucherOrderService voucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
            return voucherOrderService.createOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }


    /**
     * 如果锁加在事务里面，该事务可能还没有提交事务又进来一个新的请求
     * 还是可能发生并发问题，应该加在调用该方法之前
     *
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createOrder(Long voucherId) {
        //调用intern的目的是去字符串常量池找一样的字符串 返回过来  这样就能直接通过该字符串判断是否为同一个用户 进行加锁
        //查询当前用户订单表该优惠卷是否存在
        int count = query().eq("user_id", UserHolder.getUser().getId()).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("您已经下单该优惠卷了");
        }

        //4.扣减库存，生成订单
        boolean isSuccess = iSeckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!isSuccess) {
            return Result.fail("库存不足");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long order = redisIdWorker.nextId("order");
        voucherOrder.setId(order);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(voucherOrder);
    }

}
