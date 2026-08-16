package ink.garry.rd.agent.ws.application.agentrunner;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.attachment.AttachmentProperties;
import ink.garry.rd.agent.ws.client.attachment.AttachmentRefParam;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.attachment.valueobject.AttachmentKind;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 Open / Debug 请求归一为 {@link NormalizedInvokeContent}。
 */
@Component
public class InvokeContentNormalizer {

    @Resource
    private AttachmentProperties attachmentProperties;

    /**
     * 归一化：input 与 attachments 至少一个非空；校验数量；推导 kind。
     *
     * @param input       用户输入（Object 或 String，可空）
     * @param attachments 附件列表，可空
     * @return 归一化内容
     */
    public NormalizedInvokeContent normalize(Object input, List<AttachmentRefParam> attachments) {
        String text = toText(input);
        List<AttachmentRefParam> raw = attachments == null ? List.of() : attachments;

        if (StrUtil.isBlank(text) && CollUtil.isEmpty(raw)) {
            throw new BusinessException(BizCode.ATTACHMENT_INVALID.getCode(),
                    "input 与 attachments 至少一个有内容");
        }
        if (raw.size() > attachmentProperties.getMaxCountPerTurn()) {
            throw new BusinessException(BizCode.ATTACHMENT_COUNT_EXCEEDED.getCode(),
                    "附件数量超限，最多 " + attachmentProperties.getMaxCountPerTurn() + " 个");
        }

        List<NormalizedInvokeContent.AttachmentRef> refs = new ArrayList<>();
        for (AttachmentRefParam p : raw) {
            if (p == null || StrUtil.isBlank(p.getFileId())) {
                throw new BusinessException(BizCode.ATTACHMENT_INVALID.getCode(), "attachments[].fileId 不能为空");
            }
            String mime = StrUtil.trim(p.getMimeType());
            AttachmentKind kind;
            if (StrUtil.isNotBlank(p.getKind())) {
                kind = "image".equalsIgnoreCase(p.getKind()) || "IMAGE".equalsIgnoreCase(p.getKind())
                        ? AttachmentKind.IMAGE
                        : AttachmentKind.FILE;
            } else {
                kind = AttachmentKind.fromMime(mime);
            }
            if (p.getSize() != null && p.getSize() > attachmentProperties.getMaxSizeBytes()) {
                throw new BusinessException(BizCode.ATTACHMENT_TOO_LARGE.getCode(),
                        "附件过大: " + p.getFileId());
            }
            if (StrUtil.isNotBlank(mime)) {
                boolean allowed = attachmentProperties.getAllowedMimeTypes().stream()
                        .anyMatch(a -> a.equalsIgnoreCase(mime));
                if (!allowed) {
                    throw new BusinessException(BizCode.ATTACHMENT_TYPE_UNSUPPORTED.getCode(),
                            "不支持的附件类型: " + mime);
                }
            }
            refs.add(NormalizedInvokeContent.AttachmentRef.builder()
                    .fileId(p.getFileId().trim())
                    .name(p.getName())
                    .mimeType(mime)
                    .size(p.getSize())
                    .kind(kind)
                    .build());
        }

        return NormalizedInvokeContent.builder()
                .text(text)
                .attachments(refs)
                .build();
    }

    private static String toText(Object input) {
        if (input == null) {
            return null;
        }
        if (input instanceof String s) {
            return s;
        }
        String s = String.valueOf(input);
        return "null".equals(s) ? null : s;
    }
}
