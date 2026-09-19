package ink.garry.rd.agent.ws.infra.sandbox.oss;

import com.alibaba.opensandbox.sandbox.domain.models.sandboxes.PVC;
import com.alibaba.opensandbox.sandbox.domain.models.sandboxes.Volume;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SessionWorkspaceMount;
import ink.garry.rd.agent.ws.infra.common.client.sandbox.SandboxProperties;

/**
 * 把会话工作空间前缀收成 OpenSandbox {@link Volume}（K8s/ACS：挂已有 PVC）。
 * <p>
 * OpenSandbox Kubernetes 运行时当前仅支持 {@code pvc}/{@code host}，不支持 {@code ossfs}。
 * ACS 上由 CSI 把 OSS 静态卷挂成 PVC；本工厂只引用 claim，密钥不进 AgentOps。
 */
public final class OssWorkspaceVolumeFactory {

    private OssWorkspaceVolumeFactory() {
    }

    /**
     * @param oss     已开启的会话工作空间配置
     * @param subPath {@link SessionWorkspaceMount#subPath}
     * @return 挂到 {@code /workspace} 的会话卷（可读写，BYO PVC）
     */
    public static Volume build(SandboxProperties.OssWorkspace oss, String subPath) {
        if (oss == null || !oss.isEnabled()) {
            throw new IllegalArgumentException("会话工作空间未开启");
        }
        if (isBlank(oss.getPvcClaimName())) {
            throw new IllegalStateException("sandbox.oss 已开启，但 pvcClaimName 为空（ACS/K8s 需 BYO PVC）");
        }
        if (isBlank(subPath)) {
            throw new IllegalArgumentException("会话工作空间 subPath 不能为空");
        }
        // createIfNotExists=false：集群里已由运维创建好 OSS CSI PVC，禁止 Server 自动建盘
        PVC pvc = PVC.builder()
                .claimName(oss.getPvcClaimName().trim())
                .createIfNotExists(false)
                .build();
        return Volume.builder()
                .name("session-workspace")
                .pvc(pvc)
                .mountPath(SessionWorkspaceMount.MOUNT_PATH)
                .subPath(subPath)
                .readOnly(false)
                .build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
