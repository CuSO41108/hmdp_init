package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopDoc;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.repository.ShopRepository;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

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

    @Resource
    private UserMapper userMapper;

    @Resource
    private IShopService iShopService;

    @Resource
    private ShopRepository shopRepository;

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
    void loadShopData(){
        List<Shop> list = shopService.list();
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;

            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());

            for (Shop shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
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

    @Test
    public void generateToken() throws Exception{
        //数据库查询1000个用户的信息
        List<User> list = userService.list(new QueryWrapper<User>().last("limit 1000"));
        //创建字符输入流准备写入token到文件
        BufferedWriter br = new BufferedWriter(new FileWriter("D:\\lst\\heima\\hmdp-init\\src\\main\\resources\\Tokens.txt"));
        for (User user : list) {
            //随机生成token作为登录令牌
            String token = UUID.randomUUID().toString(true);
            //将User对象转为HashMap存储到Redis中
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName, fieldValue) ->
                                    fieldValue.toString()));
            //保存用户信息到Redis中
            String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
            stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
            stringRedisTemplate.expire(tokenKey,RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
            //写入token到文件
            br.write(token);
            br.newLine();
            br.flush();
        }

    }
    @Test
    public void loadShopDocData(){
        List<Shop> list = shopService.list();
        if (list == null && list.size() == 0) {
            log.error("MySQL中没有商铺信息");
            return;
        }
        List<ShopDoc> shopDocList = new ArrayList<>();

        for (Shop shop : list) {
            ShopDoc shopDoc = new ShopDoc();

            BeanUtil.copyProperties(shop, shopDoc);

            shopDocList.add(shopDoc);
        }
        shopRepository.saveAll(shopDocList);
        log.info("全量数据导入 ES 成功，共 {} 条", shopDocList.size());
    }
}
