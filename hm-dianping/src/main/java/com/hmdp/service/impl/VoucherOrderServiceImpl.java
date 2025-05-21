package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @Autowired
    private AopAutoConfiguration aopAutoConfiguration;

    //该类初始化完成后会进行执行
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                //1.获取阻塞队列的订单信息
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2.创建订单
                    handlerVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("订单异常", e);
                }
            }
        }

    }


    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
        //这是另外一个新的线程去做了，不是原先的线程做所以取不到ThreadLocal里面的数据
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        //反向来写最好，最好不要写嵌套
        if (!isLock) {
            //获取失败
            log.error("不允许重复下单");
            return;
        }
        //说明获取锁成功了
        try {
            proxy.createOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();

        //1.第一步执行lua脚本，有没有购买资格
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        //2.判断是否为0
        //2.1不为0没有购买资格
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //2.2为0有购买资格，把下单信息保存到阻塞队列中，后续进行数据库操作,生成的订单id

        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        //订单id
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        //保存到阻塞队列
        orderTasks.add(voucherOrder);
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //3.返回订单id
        return Result.ok(orderId);
    }


//    @Override
//    public Result seckillVoucher(Long voucherId) {
//
//        //1.查询优惠卷
//        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否开始 || 是否已经结束
//        LocalDateTime beginTime = voucher.getBeginTime();
//        LocalDateTime endTime = voucher.getEndTime();
//        if (beginTime.isAfter(LocalDateTime.now())) {
//            //开始时间在当前时间之后，还没有开始
//            return Result.fail("秒杀还没开始");
//        }
//        if (endTime.isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//        //3.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            //库存不足
//            return Result.fail("库存不足够！");
//        }
//
//        //以用户id作为锁，进行范围锁 而不是全锁
//        Long userId = UserHolder.getUser().getId();
//        //此时事务不存在，因为调用的不是Spring管理返回的代理对象，此时需要调用Spring管理的代理对象
//        //此时这样调用的service才会具有事务
////        synchronized (userId.toString().intern()) {
////            IVoucherOrderService voucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
////            return voucherOrderService.createOrder(voucherId);
////        }
////        SimpleRedisLock lock = new SimpleRedisLock("order::" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean isLock = lock.tryLock();
//        //判断是否获取锁成功
//        //反向来写最好，最好不要写嵌套
//        if (!isLock) {
//            //获取失败
//            return Result.fail("一个人只能下一单");
//        }
//        //说明获取锁成功了
//        try {
//            IVoucherOrderService voucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
//            return voucherOrderService.createOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//    }

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


    @Transactional
    public void createOrder(VoucherOrder voucherOrder) {
        save(voucherOrder);
    }

}
