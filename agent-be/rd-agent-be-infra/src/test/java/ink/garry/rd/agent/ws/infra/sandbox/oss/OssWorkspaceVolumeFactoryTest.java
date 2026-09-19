package ink.garry.rd.agent.ws.infra.sandbox.oss;

import com.alibaba.opensandbox.sandbox.domain.models.sandboxes.Volume;
import ink.garry.rd.agent.ws.infra.common.client.sandbox.SandboxProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OssWorkspaceVolumeFactoryTest {

    @Test
    void build_mountsSessionPrefixAtWorkspace() {
        SandboxProperties.OssWorkspace oss = new SandboxProperties.OssWorkspace();
        oss.setEnabled(true);
        oss.setBucket("agent-ws");
        oss.setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        oss.setAccessKeyId("ak");
        oss.setAccessKeySecret("sk");

        Volume volume = OssWorkspaceVolumeFactory.build(oss, "WS1/AGT1/SES1");

        assertEquals("/workspace", volume.getMountPath());
        assertEquals("WS1/AGT1/SES1", volume.getSubPath());
        assertFalse(volume.getReadOnly());
        assertEquals("agent-ws", volume.getOssfs().getBucket());
        assertEquals("oss-cn-hangzhou.aliyuncs.com", volume.getOssfs().getEndpoint());
    }

    @Test
    void build_rejectsIncompleteCredentials() {
        SandboxProperties.OssWorkspace oss = new SandboxProperties.OssWorkspace();
        oss.setEnabled(true);
        oss.setBucket("agent-ws");
        assertThrows(IllegalStateException.class,
                () -> OssWorkspaceVolumeFactory.build(oss, "WS1/AGT1/SES1"));
    }
}
