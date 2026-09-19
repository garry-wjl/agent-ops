package ink.garry.rd.agent.ws.infra.sandbox.oss;

import com.alibaba.opensandbox.sandbox.domain.models.sandboxes.OSSFS;
import com.alibaba.opensandbox.sandbox.domain.models.sandboxes.Volume;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SessionWorkspaceMount;
import ink.garry.rd.agent.ws.infra.common.client.sandbox.SandboxProperties;

/**
 * 把会话 OSS 前缀收成 OpenSandbox {@link Volume}。
 */
public final class OssWorkspaceVolumeFactory {

    private OssWorkspaceVolumeFactory() {
    }

    /**
     * @param oss     已开启的 OSS 配置
     * @param subPath {@link SessionWorkspaceMount#subPath}
     * @return 挂到 {@code /workspace} 的会话卷（可读写）
     */
    public static Volume build(SandboxProperties.OssWorkspace oss, String subPath) {
        if (oss == null || !oss.isEnabled()) {
            throw new IllegalArgumentException("OSS 工作空间未开启");
        }
        if (isBlank(oss.getBucket()) || isBlank(oss.getEndpoint())
                || isBlank(oss.getAccessKeyId()) || isBlank(oss.getAccessKeySecret())) {
            throw new IllegalStateException("sandbox.oss 已开启，但 bucket / endpoint / accessKey 不完整");
        }
        if (isBlank(subPath)) {
            throw new IllegalArgumentException("会话 OSS subPath 不能为空");
        }
        OSSFS fs = OSSFS.builder()
                .bucket(oss.getBucket().trim())
                .endpoint(oss.getEndpoint().trim())
                .accessKeyId(oss.getAccessKeyId().trim())
                .accessKeySecret(oss.getAccessKeySecret().trim())
                .build();
        return Volume.builder()
                .name("session-workspace")
                .ossfs(fs)
                .mountPath(SessionWorkspaceMount.MOUNT_PATH)
                .subPath(subPath)
                .readOnly(false)
                .build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
