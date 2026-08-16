package ink.garry.rd.agent.ws.application.attachment.command;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.agentrunner.NormalizedInvokeContent;
import ink.garry.rd.agent.ws.application.attachment.AttachmentProperties;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.common.oss.OssPresignResultVO;
import ink.garry.rd.agent.ws.domain.attachment.ChatAttachment;
import ink.garry.rd.agent.ws.domain.attachment.dto.OssPresignResult;
import ink.garry.rd.agent.ws.domain.attachment.factory.ChatAttachmentFactory;
import ink.garry.rd.agent.ws.domain.attachment.gateway.OssObjectGateway;
import ink.garry.rd.agent.ws.domain.attachment.valueobject.AttachmentKind;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 附件上传登记命令服务。
 * <p>
 * Common {@code sts-init} 不登记；登记发生在 Open {@code uploadAttachment} 或 Invoke 前
 * {@link #ensureRegisteredForInvoke}。
 */
@Slf4j
@Service
public class AttachmentCommandService {

    @Resource
    private AttachmentProperties attachmentProperties;

    @Resource
    private OssObjectGateway ossObjectGateway;

    @Resource
    private ChatAttachmentFactory chatAttachmentFactory;

    /**
     * 校验白名单 → OSS 预签名 → 登记 chat_attachment（Open uploadAttachment）。
     */
    @Transactional
    public OssPresignResultVO initUpload(String workspaceNum,
                                         String agentNum,
                                         String fileName,
                                         String contentType,
                                         Long sizeBytes,
                                         String operatorId) {
        if (StrUtil.isBlank(workspaceNum)) {
            throw new BusinessException(BizCode.ATTACHMENT_INVALID.getCode(), "workspaceNum 不能为空");
        }
        validateMimeAndSize(contentType, sizeBytes);
        OssPresignResult presign = ossObjectGateway.presignUpload(fileName);
        register(workspaceNum, agentNum, presign.getFileId(), fileName, contentType, sizeBytes, operatorId);
        return toVo(presign);
    }

    /**
     * Invoke 前确保本轮附件已登记：已存在则校验同空间；不存在则按引用元数据登记。
     */
    @Transactional
    public void ensureRegisteredForInvoke(String workspaceNum,
                                          String agentNum,
                                          NormalizedInvokeContent content,
                                          String operatorId) {
        if (content == null || !content.hasAttachments()) {
            return;
        }
        if (StrUtil.isBlank(workspaceNum)) {
            throw new BusinessException(BizCode.ATTACHMENT_INVALID.getCode(),
                    "当前调用缺少工作空间上下文，无法使用附件");
        }
        List<NormalizedInvokeContent.AttachmentRef> refs = content.getAttachments();
        if (CollUtil.isEmpty(refs)) {
            return;
        }
        for (NormalizedInvokeContent.AttachmentRef ref : refs) {
            if (ref == null || StrUtil.isBlank(ref.getFileId())) {
                throw new BusinessException(BizCode.ATTACHMENT_INVALID.getCode(), "attachments[].fileId 不能为空");
            }
            ChatAttachment existing = chatAttachmentFactory.createByFileId(ref.getFileId());
            if (existing != null) {
                existing.assertAccessible(workspaceNum);
                continue;
            }
            String mime = StrUtil.trim(ref.getMimeType());
            if (StrUtil.isBlank(mime) && ref.getKind() == AttachmentKind.IMAGE) {
                mime = "image/png";
            }
            String name = StrUtil.blankToDefault(ref.getName(), ref.getFileId());
            Long size = ref.getSize() != null ? ref.getSize() : 1L;
            validateMimeAndSize(mime, size);
            register(workspaceNum, agentNum, ref.getFileId(), name, mime, size, operatorId);
        }
    }

    private void register(String workspaceNum,
                          String agentNum,
                          String fileId,
                          String fileName,
                          String mimeType,
                          Long sizeBytes,
                          String operatorId) {
        ChatAttachment existing = chatAttachmentFactory.createByFileId(fileId);
        if (existing != null) {
            log.info("chat_attachment already registered fileId={}", fileId);
            return;
        }
        String op = StrUtil.blankToDefault(operatorId, "system");
        ChatAttachment attachment = chatAttachmentFactory.create(
                workspaceNum, fileId, extractBaseName(fileName), mimeType, sizeBytes, agentNum);
        attachment.save(op);
        log.info("chat_attachment registered num={} fileId={} ws={}",
                attachment.getNum(), fileId, workspaceNum);
    }

    private void validateMimeAndSize(String contentType, Long sizeBytes) {
        if (StrUtil.isBlank(contentType)) {
            throw new BusinessException(BizCode.ATTACHMENT_TYPE_UNSUPPORTED.getCode(), "contentType 不能为空");
        }
        String mime = contentType.trim().toLowerCase();
        boolean allowed = attachmentProperties.getAllowedMimeTypes().stream()
                .anyMatch(a -> a.equalsIgnoreCase(mime));
        if (!allowed) {
            throw new BusinessException(BizCode.ATTACHMENT_TYPE_UNSUPPORTED.getCode(),
                    "不支持的附件类型: " + contentType);
        }
        if (sizeBytes == null || sizeBytes <= 0) {
            throw new BusinessException(BizCode.ATTACHMENT_INVALID.getCode(), "size 非法");
        }
        if (sizeBytes > attachmentProperties.getMaxSizeBytes()) {
            throw new BusinessException(BizCode.ATTACHMENT_TOO_LARGE.getCode(),
                    "附件过大，上限 " + attachmentProperties.getMaxSizeBytes() + " 字节");
        }
    }

    private static String extractBaseName(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return "file";
        }
        int idx = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return idx >= 0 ? fileName.substring(idx + 1) : fileName;
    }

    private static OssPresignResultVO toVo(OssPresignResult presign) {
        OssPresignResultVO vo = new OssPresignResultVO();
        vo.setFileId(presign.getFileId());
        vo.setUrl(presign.getUrl());
        vo.setMethod(presign.getMethod());
        vo.setExpiration(presign.getExpiration());
        vo.setSignedHeaders(presign.getSignedHeaders());
        return vo;
    }
}
