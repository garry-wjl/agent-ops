package ink.garry.rd.agent.ws.application.agentrunner;

import ink.garry.rd.agent.ws.application.attachment.AttachmentProperties;
import ink.garry.rd.agent.ws.client.attachment.AttachmentRefParam;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.attachment.valueobject.AttachmentKind;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InvokeContentNormalizer} 单测。
 */
class InvokeContentNormalizerTest {

    private InvokeContentNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new InvokeContentNormalizer();
        AttachmentProperties props = new AttachmentProperties();
        // 反射注入
        try {
            var f = InvokeContentNormalizer.class.getDeclaredField("attachmentProperties");
            f.setAccessible(true);
            f.set(normalizer, props);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void normalize_textOnly_ok() {
        NormalizedInvokeContent c = normalizer.normalize("hello", null);
        assertEquals("hello", c.getText());
        assertFalse(c.hasAttachments());
    }

    @Test
    void normalize_attachmentOnly_ok() {
        AttachmentRefParam ref = new AttachmentRefParam();
        ref.setFileId("chat/a.png");
        ref.setMimeType("image/png");
        ref.setSize(100L);
        NormalizedInvokeContent c = normalizer.normalize(null, List.of(ref));
        assertTrue(c.hasAttachments());
        assertEquals(AttachmentKind.IMAGE, c.getAttachments().get(0).getKind());
    }

    @Test
    void normalize_empty_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> normalizer.normalize(null, null));
        assertEquals(BizCode.ATTACHMENT_INVALID.getCode(), ex.getCode());
    }

    @Test
    void normalize_countExceeded_throws() {
        List<AttachmentRefParam> list = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            AttachmentRefParam ref = new AttachmentRefParam();
            ref.setFileId("f-" + i);
            ref.setMimeType("image/png");
            list.add(ref);
        }
        BusinessException ex = assertThrows(BusinessException.class,
                () -> normalizer.normalize("x", list));
        assertEquals(BizCode.ATTACHMENT_COUNT_EXCEEDED.getCode(), ex.getCode());
    }

    @Test
    void normalize_unsupportedMime_throws() {
        AttachmentRefParam ref = new AttachmentRefParam();
        ref.setFileId("a.exe");
        ref.setMimeType("application/x-msdownload");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> normalizer.normalize("x", List.of(ref)));
        assertEquals(BizCode.ATTACHMENT_TYPE_UNSUPPORTED.getCode(), ex.getCode());
    }
}
