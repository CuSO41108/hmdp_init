
仅供学习参考，试图加一些优化

加了个rocketmq到秒杀下单板块

## 限流功能说明

已集成基于 Redis ZSet + Lua 的滑动窗口限流。

### 使用
在需要保护的方法上添加：
```
@RateLimiter(window = 1, limit = 10, message = "请求过于频繁，请稍后再试")
```
参数：
* window：时间窗口（秒）
* limit：窗口内最大请求次数
* message：触发限流时返回提示
* type：限流维度（METHOD/IP/USER）

### 相关代码位置
* 注解：`com.hmdp.limiter.annotation.RateLimiter`
* 切面：`com.hmdp.limiter.aop.RateLimiterAspect`
* Lua：`resources/limiter.lua`
* 异常：`com.hmdp.limiter.exception.RateLimitException`
* 全局异常处理：`WebExceptionAdvice`（单独捕获限流异常返回自定义 message）

### 验证
快速向 `/shop/search?keyword=火锅` 连续发 12 次请求（<1s），后两次会返回限流错误。


