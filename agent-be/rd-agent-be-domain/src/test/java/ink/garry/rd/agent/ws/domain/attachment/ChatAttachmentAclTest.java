package ink.garry.rd.agent.ws.domain.attachment;

import ink.garry.rd.agent.ws.domain.attachment.valueobject.AttachmentKind;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 附件 ACL / 禁止删除规则单测。
 */
class ChatAttachmentAclTest {

    @Test
    void assertAccessible_sameWorkspace_ok() {
        ChatAttachment a = new ChatAttachment();
        a.setWorkspaceNum("WS-1");
        a.assertAccessible("WS-1");
    }

    @Test
    void assertAccessible_otherWorkspace_forbidden() {
        ChatAttachment a = new ChatAttachment();
        a.setWorkspaceNum("WS-1");
        BusinessException ex = assertThrows(BusinessException.class, () -> a.assertAccessible("WS-2"));
        assertEquals(4103, ex.getCode());
    }

    @Test
    void delete_forbidden() {
        ChatAttachment a = new ChatAttachment();
        BusinessException ex = assertThrows(BusinessException.class, () -> a.delete("u1"));
        assertEquals(4107, ex.getCode());
    }

    @Test
    void kind_fromMime() {
        assertEquals(AttachmentKind.IMAGE, AttachmentKind.fromMime("image/png"));
        assertEquals(AttachmentKind.FILE, AttachmentKind.fromMime("application/pdf"));
    }
}
