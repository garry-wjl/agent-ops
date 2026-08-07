package ink.garry.rd.agent.ws.infra.auth.audit;

import ink.garry.rd.agent.ws.infra.common.util.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 登录 / 鉴权审计辅助。
 * <p>
 * 当前 rd-agent-be 暂无审计 DB 表，先以结构化日志承载，便于后续接入 ELK / SLS。
 * 日志关键字 {@code AUTH_AUDIT}：可用于过滤；trace_id 来自 {@link TraceContext}，
 * 与请求日志贯通。
 * <p>
 * TODO: 接入审计表后改写为 DB 落库，保留 logger 输出作 fallback。
 */
@Slf4j
@Component
public class AuthAuditHelper {

    private static final String TAG = "AUTH_AUDIT";

    /**
     * 登录成功。
     *
     * @param userAd AD 账号
     */
    public void recordLoginSuccess(String userAd) {
        log.info("{} event=LOGIN_SUCCESS user={} trace={}", TAG, userAd, TraceContext.get());
    }

    /**
     * 登录失败。
     *
     * @param reason 失败原因（错误码或异常类名）
     */
    public void recordLoginFailed(String reason) {
        log.warn("{} event=LOGIN_FAILED reason={} trace={}", TAG, reason, TraceContext.get());
    }

    /**
     * 鉴权拒绝（带 token 但解析失败 / 无权访问）。
     *
     * @param userAd 用户 AD 账号（可能为 null）
     * @param path   请求路径
     * @param reason 拒绝原因
     */
    public void recordAccessDenied(String userAd, String path, String reason) {
        log.warn("{} event=ACCESS_DENIED user={} path={} reason={} trace={}",
                TAG, userAd, path, reason, TraceContext.get());
    }

    /**
     * 登出。
     *
     * @param userAd AD 账号
     */
    public void recordLogout(String userAd) {
        log.info("{} event=LOGOUT user={} trace={}", TAG, userAd, TraceContext.get());
    }
}
