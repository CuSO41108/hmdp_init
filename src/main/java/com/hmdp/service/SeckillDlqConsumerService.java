package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
@RocketMQMessageListener(topic = "%DLQ%seckill_order_consumer_group",
                        consumerGroup = "dlq_seckill_order_consumer")
public class SeckillDlqConsumerService implements RocketMQListener<VoucherOrder> {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(VoucherOrder failedOrder) {
        try {
            log.error("[死信队列收到消息]订单16次重试均失败，执行补偿。订单: {}", failedOrder);

            // 1. 将 Redis 库存加回来
            stringRedisTemplate.opsForValue().increment("seckill:stock:" + failedOrder.getVoucherId());

            // 2. 将用户从 LUA 脚本记录的 "已购买" Set 集合中移除，允许他重新抢
            stringRedisTemplate.opsForSet().remove(
                    "seckill:order:" + failedOrder.getVoucherId(),
                    failedOrder.getUserId().toString()
            );
            log.info("Redis 库存与用户资格补偿完毕。");
            // （推荐）3. 记录失败日志到数据库，以便人工核查
//             failedLogService.saveFailedOrder(failedOrder);
            
        } catch (Exception e) {
            log.error("[死信队列处理器异常]补偿失败，请人工干预: {}", failedOrder, e);
        }
        // 注意：DLQ 处理器绝对不能再抛出异常，必须 try-catch 兜底所有。
    }
}
