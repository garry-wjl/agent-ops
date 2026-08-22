package ink.garry.rd.agent.ws.application.auth.command;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.auth.LoginSecurityProperties;
import ink.garry.rd.agent.ws.infra.common.constant.RedisKeyConstant;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 登录失败计数（用户名 + IP），超过阈值后要求滑块校验。
 */
@Service
public class LoginFailCounterService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private LoginSecurityProperties loginSecurityProperties;

    /**
     * 当前是否已达滑块阈值。
     *
     * @param username 用户名
     * @param clientIp 客户端 IP
     * @return true 表示必须带有效滑块
     */
    public boolean requiresCaptcha(String username, String clientIp) {
        long count = currentCount(username, clientIp);
        return count >= loginSecurityProperties.getFailThreshold();
    }

    /**
     * 读取当前失败次数。
     *
     * @param username 用户名
     * @param clientIp 客户端 IP
     * @return 次数，无记录为 0
     */
    public long currentCount(String username, String clientIp) {
        String key = key(username, clientIp);
        String raw = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(raw)) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 密码错误时自增失败次数。
     *
     * @param username 用户名
     * @param clientIp 客户端 IP
     * @return 自增后的次数
     */
    public long increment(String username, String clientIp) {
        String key = key(username, clientIp);
        long count = currentCount(username, clientIp) + 1;
        // 不使用 stringRedisTemplate.expire(...)：当前工程 RedisConnection
        // （DefaultedRedisConnection#pExpire）会对 Duration/TimeUnit 的 expire 产生自递归 StackOverflow。
        // 用带 TTL 的 SET 写入计数即可。
        stringRedisTemplate.opsForValue().set(
                key,
                Long.toString(count),
                Math.max(1L, loginSecurityProperties.getFailTtlMinutes()),
                TimeUnit.MINUTES);
        return count;
    }

    /**
     * 登录成功后清除失败计数。
     *
     * @param username 用户名
     * @param clientIp 客户端 IP
     */
    public void clear(String username, String clientIp) {
        stringRedisTemplate.delete(key(username, clientIp));
    }

    private String key(String username, String clientIp) {
        String u = StrUtil.blankToDefault(username, "").trim().toLowerCase(Locale.ROOT);
        String ip = StrUtil.blankToDefault(clientIp, "unknown").trim();
        return RedisKeyConstant.AUTH_LOGIN_FAIL_PREFIX + u + ":" + ip;
    }
}
