package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryTypeList() {
        List<String> list = redisTemplate.opsForList().range(RedisConstants.CACHE_LIST_KEY, 0, -1);
        List<ShopType> shopTypeList = new ArrayList<>();
        if (CollectionUtil.isNotEmpty(list)) {
            for (String item : list) {
                shopTypeList.add(JSONUtil.toBean(item, ShopType.class));
            }
            return Result.ok(shopTypeList);
        }

        //查询数据库
        shopTypeList = baseMapper.selectList(new QueryWrapper<>());
        if(CollectionUtil.isEmpty(shopTypeList)){
            return Result.fail("商品分类不存在");
        }

        List<String> jsonList = new ArrayList<>();
        for (ShopType shopType : shopTypeList) {
            jsonList.add(JSONUtil.toJsonStr(shopType));
        }

        redisTemplate.opsForList().leftPushAll(RedisConstants.CACHE_LIST_KEY,jsonList);
        return Result.ok(shopTypeList);
    }
}
