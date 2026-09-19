package ink.garry.rd.agent.ws.infra.sandbox.oss;

import com.alibaba.opensandbox.sandbox.domain.models.sandboxes.Volume;
import ink.garry.rd.agent.ws.infra.common.client.sandbox.SandboxProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OssWorkspaceVolumeFactoryTest {

    @Test
    void build_mountsSessionPrefixOnByoPvc() {
        SandboxProperties.OssWorkspace oss = new SandboxProperties.OssWorkspace();
        oss.setEnabled(true);
        oss.setPvcClaimName("agent-ops-workspace");

        Volume volume = OssWorkspaceVolumeFactory.build(oss, "WS1/AGT1/SES1");

        assertEquals("/workspace", volume.getMountPath());
        assertEquals("WS1/AGT1/SES1", volume.getSubPath());
        assertFalse(volume.getReadOnly());
        assertNull(volume.getOssfs());
        assertEquals("agent-ops-workspace", volume.getPvc().getClaimName());
        assertFalse(volume.getPvc().getCreateIfNotExists());
    }

    @Test
    void build_rejectsMissingClaimName() {
        SandboxProperties.OssWorkspace oss = new SandboxProperties.OssWorkspace();
        oss.setEnabled(true);
        assertThrows(IllegalStateException.class,
                () -> OssWorkspaceVolumeFactory.build(oss, "WS1/AGT1/SES1"));
    }

    @Test
    void build_rejectsBlankSubPath() {
        SandboxProperties.OssWorkspace oss = new SandboxProperties.OssWorkspace();
        oss.setEnabled(true);
        oss.setPvcClaimName("agent-ops-workspace");
        assertThrows(IllegalArgumentException.class,
                () -> OssWorkspaceVolumeFactory.build(oss, "  "));
    }
}
