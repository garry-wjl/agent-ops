package ink.garry.rd.agent.ws.application.agentrunner;

import ink.garry.rd.agent.ws.domain.attachment.valueobject.AttachmentKind;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.List;

/**
 * Open / Debug Invoke 归一化后的用户内容。
 */
@Value
@Builder
public class NormalizedInvokeContent {

    /** 用户文本；可空 */
    String text;

    /** 附件引用；永不 null */
    @Builder.Default
    List<AttachmentRef> attachments = Collections.emptyList();

    /**
     * 是否含附件。
     *
     * @return true 有附件
     */
    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }

    /**
     * 单条附件引用。
     */
    @Value
    @Builder
    public static class AttachmentRef {
        String fileId;
        String name;
        String mimeType;
        Long size;
        AttachmentKind kind;
    }
}
