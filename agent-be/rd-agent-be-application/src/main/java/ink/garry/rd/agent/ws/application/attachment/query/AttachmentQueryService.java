package ink.garry.rd.agent.ws.application.attachment.query;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.attachment.AttachmentProperties;
import ink.garry.rd.agent.ws.application.workspace.WorkspaceQueryService;
import ink.garry.rd.agent.ws.client.attachment.ChatAttachmentDTO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.attachment.ChatAttachment;
import ink.garry.rd.agent.ws.domain.attachment.factory.ChatAttachmentFactory;
import ink.garry.rd.agent.ws.domain.attachment.gateway.DocumentParseGateway;
import ink.garry.rd.agent.ws.domain.attachment.gateway.OssObjectGateway;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.util.UserContextHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 附件查询 / ACL / Tool 抽取。
 */
@Service
public class AttachmentQueryService {

    @Resource
    private ChatAttachmentFactory chatAttachmentFactory;

    @Resource
    private OssObjectGateway ossObjectGateway;

    @Resource
    private DocumentParseGateway documentParseGateway;

    @Resource
    private AttachmentProperties attachmentProperties;

    @Resource
    private WorkspaceQueryService workspaceQueryService;

    /**
     * 断言同工作空间可读，返回 DTO。
     *
     * @param fileId       OSS 对象 ID
     * @param workspaceNum 工作空间
     * @return DTO
     */
    public ChatAttachmentDTO assertReadable(String fileId, String workspaceNum) {
        ChatAttachment attachment = requireAttachment(fileId);
        attachment.assertAccessible(workspaceNum);
        return toDto(attachment);
    }

    /**
     * 换签下载 URL（按调用方工作空间 ACL；未登记则直接换签）。
     *
     * @param fileId       OSS 对象 ID
     * @param workspaceNum 工作空间（可空则跳过 ACL，仅未登记或兼容路径）
     * @return 签名 URL
     */
    public String getDownloadUrl(String fileId, String workspaceNum) {
        ChatAttachment attachment = chatAttachmentFactory.createByFileId(fileId);
        if (attachment != null && StrUtil.isNotBlank(workspaceNum)) {
            attachment.assertAccessible(workspaceNum);
        }
        return ossObjectGateway.generateDownloadUrl(fileId);
    }

    /**
     * Common file-url：不依赖请求头空间。
     * <ul>
     *   <li>未登记 → 直接换签（技能包等存量对象）</li>
     *   <li>已登记 → 当前登录用户须为附件所属空间成员</li>
     * </ul>
     *
     * @param fileId OSS 对象 ID
     * @return 签名 URL
     */
    public String getDownloadUrlForCurrentUser(String fileId) {
        ChatAttachment attachment = chatAttachmentFactory.createByFileId(fileId);
        if (attachment == null) {
            return ossObjectGateway.generateDownloadUrl(fileId);
        }
        String userId = UserContextHolder.currentUserId();
        if (StrUtil.isBlank(userId)) {
            throw new BusinessException(BizCode.UNAUTHORIZED.getCode(), "未登录");
        }
        String role = workspaceQueryService.getMyRole(attachment.getWorkspaceNum(), userId);
        if (role == null) {
            throw new BusinessException(BizCode.ATTACHMENT_FORBIDDEN.getCode(), "无权访问该附件");
        }
        return ossObjectGateway.generateDownloadUrl(fileId);
    }

    /**
     * Tool 抽取文档文本。
     *
     * @param fileId       OSS 对象 ID
     * @param workspaceNum 工作空间
     * @param maxChars     截断上限；null 用配置默认
     * @return 纯文本
     */
    public String extractForTool(String fileId, String workspaceNum, Integer maxChars) {
        ChatAttachmentDTO dto = assertReadable(fileId, workspaceNum);
        int limit = maxChars != null && maxChars > 0
                ? maxChars
                : attachmentProperties.getReadMaxChars();
        return documentParseGateway.extractText(dto.getFileId(), dto.getMimeType(), limit);
    }

    private ChatAttachment requireAttachment(String fileId) {
        if (StrUtil.isBlank(fileId)) {
            throw new BusinessException(BizCode.ATTACHMENT_INVALID.getCode(), "fileId 不能为空");
        }
        ChatAttachment attachment = chatAttachmentFactory.createByFileId(fileId);
        if (attachment == null) {
            throw new BusinessException(BizCode.ATTACHMENT_NOT_FOUND.getCode(), "附件不存在");
        }
        return attachment;
    }

    private static ChatAttachmentDTO toDto(ChatAttachment a) {
        return ChatAttachmentDTO.builder()
                .num(a.getNum())
                .workspaceNum(a.getWorkspaceNum())
                .fileId(a.getFileId())
                .fileName(a.getFileName())
                .mimeType(a.getMimeType())
                .sizeBytes(a.getSizeBytes())
                .kind(a.getKind() != null ? a.getKind().name() : null)
                .agentNum(a.getAgentNum())
                .build();
    }
}
