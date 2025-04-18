package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;


    @Override
    public Result queryById(Long id) {
        //缓存击穿,使用互斥锁
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("该数据可能不存在");
        }
        return Result.ok(shop);
    }


    //缓存击穿
    public Shop queryWithMutex(Long id) {
        String key = "";
        Shop shop = null;
        try {
            //从redis查询缓存
            key = RedisConstants.CACHE_SHOP_KEY + id;
            String shopJson = redisTemplate.opsForValue().get(key);
            //判断是否存在
            if (StringUtils.isNotBlank(shopJson)) {
                //直接返回
                shop = JSONUtil.toBean(shopJson, Shop.class);
                return shop;
            }
            //获取互斥锁
            if (shopJson == null) {
                return null;
            }

            //获取锁
            boolean isLock = tryLock(RedisConstants.LOCK_SHOP_KEY + id);
            if (!isLock) {
                Thread.sleep(50);
                return queryWithMutex(id);//继续循环
            }

            //模拟延迟
            Thread.sleep(200);
            //不存在，根据id查询数据库
            shop = getById(id);
            //查询后不存在 返回错误
            if (Objects.isNull(shop)) {
                //将空值写入redis
                redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //存在先写入redis，然后再去返回
            redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            unLock(key);
        }
        //返回

        //释放锁

        //返回

        return shop;
    }


    public void saveShop2Redis(Long id, Long expireSeconds) {
        //1.查询店铺数据
        Shop shop = getById(id);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //缓存穿透逻辑过期处理方法
    public Shop queryWithLogicalExpire(Long id) {
        //从redis查询缓存
        String shopJson = redisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //判断是否存在
        if (StringUtils.isBlank(shopJson)) {
            return null;
        }

        //如果存在就复杂了

        //5.判断是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        //5.1如果未过期直接返回
        //判断过期时间是否在当前时间之后
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            //未过期直接返回
            return shop;
        }
        //5.2如果过期进行重新查询
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        if (isLock) {
            //成功开启独立线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try{
                    this.saveShop2Redis(id,30L);
                    //释放锁
                }catch (Exception e){
                    e.printStackTrace();
                }finally {
                    unLock(lockKey);
                }
            });
        }
        //返回
        return shop;
    }

    //缓存穿透
    public Shop queryWithPassThrough(Long id) {
        //从redis查询缓存
        String shopJson = redisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //判断是否存在
        if (StringUtils.isNotBlank(shopJson)) {
            //直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //命中是否为空值
        if (shopJson != null) {
            //返回一个错误信息
            return null;
        }
        //不存在，根据id查询数据库
        Shop shop = getById(id);
        //查询后不存在 返回错误
        if (Objects.isNull(shop)) {
            //将空值写入redis
            redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在先写入redis，然后再去返回
        redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回
        return shop;
    }

    //获取所
    private boolean tryLock(String key) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        Boolean flag = redisTemplate.delete(key);
    }

    @Override
    @Transactional  //如果有异常整个事务都需要进行回滚
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (Objects.isNull(id)) {
            return Result.fail("店铺id不能为空");
        }

        //经过讨论后得出的方案，先进行删数据库 再删缓存
        updateById(shop);

        //进行缓存操作
        redisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
