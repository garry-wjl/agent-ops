package ink.garry.rd.agent.ws.infra.common.client.sandbox;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 远程沙箱接入参数，统一从 {@code application.yml} 的 {@code sandbox.*} 注入。
 * <p>
 * 安全约束：{@link #apiKey} 必须走 K8s Secret / 环境变量注入，禁止明文落 git。
 * <p>
 * {@link #provider}：
 * <ul>
 *   <li>{@code opensandbox} — 既有 OpenSandbox SDK（默认，兼容存量）；</li>
 *   <li>{@code acs} — ACS Agent Sandbox（E2B / ack-sandbox-manager）。</li>
 * </ul>
 * 会话级 OSS subPath 隔离已下线：{@link OssWorkspace#enabled} 应保持 {@code false}。
 */
@Data
@ConfigurationProperties(prefix = "sandbox")
public class SandboxProperties {

    /**
     * 沙箱后端：{@code opensandbox}（默认）或 {@code acs}。
     * {@code sandbox.mock=true} 时强制走本机 Docker，忽略本字段。
     */
    private String provider = "opensandbox";

    /** 沙箱服务 API Key；仅注入，禁止序列化输出。 */
    private String apiKey;

    /**
     * 逻辑域名（不含协议）。
     * <p>OpenSandbox：网关 host。ACS：E2B domain（Host = {@code api.&#123;domain&#125;} /
     * {@code 49983-&#123;id&#125;.&#123;domain&#125;}）。
     */
    private String domain;

    /**
     * ACS 可选：manager 直连地址（host:port），本地 port-forward 用。
     * 空则 {@code &#123;protocol&#125;://api.&#123;domain&#125;}。
     */
    private String managerEndpoint;

    /**
     * ACS 可选：gateway 直连地址（host:port），本地 port-forward 用。
     * 空则 {@code &#123;protocol&#125;://&#123;domain&#125;}。
     */
    private String gatewayEndpoint;

    /** 网关协议，默认 {@code https}。 */
    private String protocol = "https";

    /** OpenSandbox 固定容器镜像。 */
    private String image = "opensandbox/code-interpreter:v1.0.2";

    /** ACS SandboxSet / E2B template 名，默认 {@code code-interpreter}。 */
    private String template = "code-interpreter";

    /**
     * 容器内 workspace 根路径。空则按 {@link #provider} 自动选择：
     * ACS → {@code /home/user}（E2B 镜像用户家目录可写）；OpenSandbox → {@code /workspace}。
     */
    private String workspaceRoot;

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
     * 本地模式（{@code sandbox.mock=true}）是否用 Docker 替代远程沙箱。
     * <p>
     * {@code true}：本机 Docker 生命周期 + exec（需 Docker Desktop / daemon）；
     * {@code false}：按 {@link #provider} 走远程网关。禁止空实现。
     */
    private boolean mock = false;

    /**
     * 本地 Docker 沙箱镜像（仅 {@link #mock}=true 时生效）。
     * <p>默认 {@code ubuntu:22.04}（需含 sh/tar/base64）；可改为预拉取的 code-interpreter 镜像。
     */
    private String dockerImage = "ubuntu:22.04";

    /**
     * 会话工作空间（已废弃：会话级 OSS subPath 隔离）。
     * <p>
     * 保持 {@code enabled=false}。会话内数据依赖「同会话复用同一沙箱实例」；
     * 跨会话持久化请用平台侧存储，不再按 session 挂 PVC。
     */
    private OssWorkspace oss = new OssWorkspace();

    /**
     * 会话工作空间卷配置（配置前缀仍为 {@code sandbox.oss}，兼容已有开关环境变量）。
     * @deprecated 会话级 OSS 隔离已移除；请保持 enabled=false。
     */
    @Data
    @Deprecated
    public static class OssWorkspace {

        /** 必须为 false（会话级 OSS 已下线） */
        private boolean enabled = false;

        /**
         * OpenSandbox 创建沙箱时引用的 PVC 名（同命名空间），例如 {@code agent-ops-workspace}。
         * <p>对应 API {@code volumes[].pvc.claimName}，且 {@code createIfNotExists=false}。
         */
        private String pvcClaimName;
    }

    /** 是否 ACS 后端。 */
    public boolean isAcs() {
        return "acs".equalsIgnoreCase(provider);
    }

    /** 是否 OpenSandbox 后端（且非 mock）。 */
    public boolean isOpenSandbox() {
        return !mock && !isAcs();
    }

    /**
     * 解析容器内 workspace 根：显式配置优先，否则 ACS 用 {@code /home/user}，其余用 {@code /workspace}。
     */
    public String resolveWorkspaceRoot() {
        if (workspaceRoot != null && !workspaceRoot.isBlank()) {
            return workspaceRoot.trim();
        }
        return isAcs() ? "/home/user" : "/workspace";
    }
}
