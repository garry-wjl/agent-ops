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

    /**
     * 本地模式（{@code sandbox.mock=true}）是否用 Docker 替代远程 OpenSandbox。
     * <p>
     * {@code true}：本机 Docker 生命周期 + exec（需 Docker Desktop / daemon）；
     * {@code false}：生产 OpenSandbox 网关。禁止空实现。
     */
    private boolean mock = false;

    /**
     * 本地 Docker 沙箱镜像（仅 {@link #mock}=true 时生效）。
     * <p>默认 {@code ubuntu:22.04}（需含 sh/tar/base64）；可改为预拉取的 code-interpreter 镜像。
     */
    private String dockerImage = "ubuntu:22.04";

    /**
     * 会话工作空间 OSS。开启后每个会话把独立前缀挂到容器 {@code /workspace}。
     * 密钥走环境变量，禁止明文落 git。
     */
    private OssWorkspace oss = new OssWorkspace();

    /**
     * 会话 OSS 卷配置。
     */
    @Data
    public static class OssWorkspace {

        /** 关闭时不挂卷，热池行为不变 */
        private boolean enabled = false;

        private String bucket;

        private String endpoint;

        private String accessKeyId;

        private String accessKeySecret;
    }
}
