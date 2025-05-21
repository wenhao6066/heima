-- 参数列表
-- 1.1优惠卷id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]

--库存key和订单key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

--脚本业务
--判断库存是否充足
if(tonumber(redis.call('get',stockKey)) <= 0) then
    return 1
end

--判断用户是否下单 SISMEMBER orderKey userId
if(tonumber(redis.call('SISMEMBER',orderKey,userId)) == 1) then
    --说明是重复下单
    return 2
end

--扣库存
redis.call('incrby',stockKey,-1)
--将该用户增加到集合里面
redis.call('sadd',orderKey,userId)
return 0