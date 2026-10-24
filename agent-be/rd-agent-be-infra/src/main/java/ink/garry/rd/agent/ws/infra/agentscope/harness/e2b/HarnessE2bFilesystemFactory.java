package ink.garry.rd.agent.ws.infra.agentscope.harness.e2b;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.infra.common.client.sandbox.SandboxProperties;
import io.agentscope.extensions.oss.OssSnapshotSpec;
import io.agentscope.extensions.sandbox.e2b.E2bFilesystemSpec;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 从平台 {@link SandboxProperties} + 全局 {@code oss.*} 装配 Harness {@link E2bFilesystemSpec}。
 * <p>
 * 生命周期交 E2B（create/resume/shutdown）；快照统一 {@link OssSnapshotSpec}。
 */
@Slf4j
@Component
public class HarnessE2bFilesystemFactory {

    @Resource
    private SandboxProperties sandboxProperties;

    @Value("${oss.endpoint}")
    private String ossEndpoint;

    @Value("${oss.access-key}")
    private String ossAccessKey;

    @Value("${oss.secret-key}")
    private String ossSecretKey;

    @Value("${oss.bucket-name}")
    private String ossBucketName;

    /**
     * 构造带 OSS 快照与隔离级别的 E2B filesystem spec。
     *
     * @param isolationScope  SESSION / USER / AGENT / GLOBAL（非法则 SESSION）
     * @param workspaceNum    工作空间编号（快照 key 前缀）
     * @param agentNum        Agent 编号（快照 key 前缀）
     * @param environment     注入沙箱的环境变量（可空）
     */
    public E2bFilesystemSpec create(String isolationScope,
                                    String workspaceNum,
                                    String agentNum,
                                    Map<String, String> environment) {
        String apiKey = sandboxProperties.getApiKey();
        if (StrUtil.isBlank(apiKey)) {
            throw new IllegalStateException(
                    "sandbox.api-key 未配置：启用沙箱时需要 SANDBOX_API_KEY / sandbox.api-key");
        }
        String domain = sandboxProperties.getDomain();
        if (StrUtil.isBlank(domain)) {
            throw new IllegalStateException("sandbox.domain 未配置");
        }

        String keyPrefix = "agent-sandbox-snaps/"
                + StrUtil.blankToDefault(workspaceNum, "_")
                + "/"
                + StrUtil.blankToDefault(agentNum, "_");
        OssSnapshotSpec snapshotSpec = new OssSnapshotSpec(
                ossEndpoint,
                ossAccessKey,
                ossSecretKey,
                ossBucketName,
                keyPrefix);

        String workspaceRoot = sandboxProperties.resolveWorkspaceRoot();
        E2bFilesystemSpec spec = new E2bFilesystemSpec()
                .apiKey(apiKey.trim())
                .domain(domain.trim())
                .apiBaseUrl(sandboxProperties.resolveApiBaseUrl())
                .templateId(StrUtil.blankToDefault(sandboxProperties.getTemplate(), "code-interpreter"))
                .workspaceRoot(workspaceRoot)
                .sandboxTimeoutSeconds(sandboxProperties.resolveSandboxTimeoutSeconds())
                .snapshotSpec(snapshotSpec);

        IsolationScope scope = parseIsolationScope(isolationScope);
        spec.isolationScope(scope);

        if (environment != null && !environment.isEmpty()) {
            WorkspaceSpec workspaceSpec = new WorkspaceSpec();
            workspaceSpec.setRoot(workspaceRoot);
            workspaceSpec.setEnvironment(environment);
            spec.workspaceSpec(workspaceSpec);
        }

        log.info("[e2b-fs] assembled domain={} apiBaseUrl={} template={} isolation={} keyPrefix={}",
                domain, sandboxProperties.resolveApiBaseUrl(),
                sandboxProperties.getTemplate(), scope, keyPrefix);
        return spec;
    }

    static IsolationScope parseIsolationScope(String raw) {
        if (StrUtil.isBlank(raw)) {
            return IsolationScope.SESSION;
        }
        try {
            return IsolationScope.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return IsolationScope.SESSION;
        }
    }
}
