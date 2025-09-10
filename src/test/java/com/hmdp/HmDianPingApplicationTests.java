package com.hmdp;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.service.IUserService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
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

    @Resource
    private IUserService userService;

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

    private static final int THREAD_COUNT = 5000; // 并发请求数
    private static final Long VOUCHER_ID = 11L;

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
