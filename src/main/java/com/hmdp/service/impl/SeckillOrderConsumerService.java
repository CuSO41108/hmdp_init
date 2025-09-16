package com.hmdp.service.impl;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
@RocketMQMessageListener(
        topic = "TOPIC_SECKILL_ORDER",
        consumerGroup = "seckill_order_consumer_group")
public class SeckillOrderConsumerService implements RocketMQListener<VoucherOrder> {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Override
    public void onMessage(VoucherOrder voucherOrder) {

        log.info("接收到秒杀订单信息:{}", voucherOrder);

        try {
            ((VoucherOrderServiceImpl) voucherOrderService).handleVoucherOrder(voucherOrder);
        }catch (Exception e){
            log.error("处理秒杀订单失败！订单详情: {}", voucherOrder, e);
            // 抛出异常，触发 RocketMQ 的自动重试机制
//            throw new RuntimeException("消费失败，触发重试", e);
        }
    }
}
