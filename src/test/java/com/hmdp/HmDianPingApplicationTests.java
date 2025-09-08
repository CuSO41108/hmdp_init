package com.hmdp;

import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
@Slf4j
public class HmDianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IVoucherOrderService voucherOrderService;

    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " +id);
            }
            latch.countDown();
        };
        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - start));
    }

    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop =shopService.getById(9L);

        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 9L, shop, 10L, TimeUnit.SECONDS);
    }

    @Test
    public void generateTokens() throws IOException {
        // 文件输出路径（改成你自己的绝对路径，方便 JMeter 读取）
        File file = new File("D:\\lst\\heima\\hmdp-init\\tokens.csv");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {


            for (int i = 0; i < 1000; i++) {
                long userId = 2000L + i;
                Map<String, String> userMap = new HashMap<>();
                userMap.put("id", String.valueOf(userId));
                userMap.put("nickName", "压测用户" + i);

                String token = UUID.randomUUID().toString();
                String userJson = "{\"id\":" + userId + ",\"nickName\":\"压测用户" + i + "\"}";

                // 1. 存入 Redis（有效期30分钟，你也可以改成1小时）
                stringRedisTemplate.opsForHash().putAll(
                        "login:token:" + token,
                        userMap
                );
                stringRedisTemplate.expire("login:token:" + token, Duration.ofMinutes(30));
                // 2. 写入 CSV 文件，每行一个 token
                writer.write(token);
                writer.newLine();
            }
        }
        System.out.println("✅ Token 生成完成，文件路径：" + file.getAbsolutePath());
    }



    private static final int THREAD_COUNT = 5000; // 并发请求数
    private static final Long VOUCHER_ID = 10L;

    @Test
    public void testSeckillConcurrency() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(200);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        long start = System.currentTimeMillis();

        for (int i = 0; i < THREAD_COUNT; i++) {
            long userId = i + 1; // 模拟不同用户
            executor.submit(() -> {
                try {
                    // 模拟设置用户上下文（伪造 ThreadLocal）
                    UserDTO user = new UserDTO();
                    user.setId(userId);
                    user.setNickName("user" + userId);
                    user.setIcon(null);
                    com.hmdp.utils.UserHolder.saveUser(user);
//                    com.hmdp.utils.UserHolder.saveUser(new com.hmdp.dto.UserDTO(userId, "user" + userId));

                    voucherOrderService.seckillVoucher(VOUCHER_ID);
                } catch (Exception e) {
                    log.error("下单失败", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long end = System.currentTimeMillis();
        log.info("并发下单完成，总耗时：{} ms", (end - start));
    }
}
