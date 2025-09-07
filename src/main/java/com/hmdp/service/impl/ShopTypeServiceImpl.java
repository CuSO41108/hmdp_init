package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList(){

        String key = CACHE_SHOP_TYPE_KEY;
        String typeJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(typeJson)) {
            List<ShopType> shopTypeList= JSONUtil.toList(typeJson, ShopType.class);
            return Result.ok(shopTypeList);
        }
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        if (shopTypeList == null || shopTypeList.isEmpty()) {
            return Result.fail("商铺类型不存在..");
        }
        stringRedisTemplate.opsForValue().set(
                key, JSONUtil.toJsonStr(shopTypeList));

        return Result.ok(shopTypeList);
    }
}
