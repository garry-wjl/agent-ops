package ink.garry.rd.agent.ws.infra.common.client.sandbox;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenSandbox 远程沙箱接入参数，统一从 {@code application.yml} 的 {@code sandbox.*} 注入。
 * <p>
 * 安全约束：{@link #apiKey} 必须走 K8s Secret / 环境变量注入，禁止明文落 git。
 */
@Data
@ConfigurationProperties(prefix = "sandbox")
public class SandboxProperties {

    /** 沙箱服务 API Key；仅注入，禁止序列化输出。 */
    private String apiKey;

    /** 沙箱服务网关域名（不含协议），例如 {@code sandbox.garry.internal}。 */
    private String domain;

    /** 网关协议，默认 {@code https}。 */
    private String protocol = "https";

    /** 固定容器镜像；本服务所有会话沙箱统一用此镜像。 */
    private String image = "opensandbox/code-interpreter:v1.0.2";

    /**
     * 沙箱容器存活 TTL（分钟），默认 10。
     * <p>
     * 同时作为：① 容器创建时的 {@code timeout}；② Redis 会话→沙箱映射的过期时长；
     * ③ 会话活动时的续期窗口。会话静默超过该时长后容器由运行时自动回收。
     */
    private long ttlMinutes = 10L;

    /** SDK HTTP 请求超时（秒），默认 30。 */
    private int requestTimeoutSeconds = 30;
}
