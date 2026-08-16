package ink.garry.rd.agent.ws.domain.attachment.factory;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.attachment.ChatAttachment;
import ink.garry.rd.agent.ws.domain.attachment.gateway.ChatAttachmentGateway;
import ink.garry.rd.agent.ws.domain.attachment.repository.ChatAttachmentRepository;
import ink.garry.rd.agent.ws.domain.attachment.valueobject.AttachmentKind;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 聊天附件领域工厂。
 */
@Component
public class ChatAttachmentFactory {

    @Resource
    private ChatAttachmentRepository chatAttachmentRepository;

    @Resource
    private ChatAttachmentGateway chatAttachmentGateway;

    /**
     * 新建附件登记（未落库）。
     *
     * @param workspaceNum 工作空间
     * @param fileId       OSS 对象 ID
     * @param fileName     原始名
     * @param mimeType     MIME
     * @param sizeBytes    大小
     * @param agentNum     关联 Agent，可空
     * @return 可 save 的聚合
     */
    public ChatAttachment create(String workspaceNum,
                                 String fileId,
                                 String fileName,
                                 String mimeType,
                                 Long sizeBytes,
                                 String agentNum) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Assert.notBlank(fileId, "fileId 不能为空");
        Assert.notBlank(fileName, "fileName 不能为空");
        Assert.notBlank(mimeType, "mimeType 不能为空");
        Assert.notNull(sizeBytes, "sizeBytes 不能为空");

        AttachmentKind kind = AttachmentKind.fromMime(mimeType);
        return new ChatAttachment(
                workspaceNum, fileId, fileName, mimeType, sizeBytes, kind, agentNum,
                chatAttachmentRepository, chatAttachmentGateway);
    }

    /**
     * 按业务号加载并装配依赖。
     *
     * @param num 业务号
     * @return 聚合；不存在返回 null
     */
    public ChatAttachment createByNum(String num) {
        Assert.notBlank(num, "num 不能为空");
        ChatAttachment attachment = chatAttachmentRepository.findByNum(num);
        if (attachment == null) {
            return null;
        }
        wire(attachment);
        return attachment;
    }

    /**
     * 按 fileId 加载并装配依赖。
     *
     * @param fileId OSS 对象 ID
     * @return 聚合；不存在返回 null
     */
    public ChatAttachment createByFileId(String fileId) {
        Assert.notBlank(fileId, "fileId 不能为空");
        ChatAttachment attachment = chatAttachmentRepository.findByFileId(fileId);
        if (attachment == null) {
            return null;
        }
        wire(attachment);
        return attachment;
    }

    private void wire(ChatAttachment attachment) {
        attachment.setChatAttachmentRepository(chatAttachmentRepository);
        attachment.setChatAttachmentGateway(chatAttachmentGateway);
        if (attachment.getKind() == null && StrUtil.isNotBlank(attachment.getMimeType())) {
            attachment.setKind(AttachmentKind.fromMime(attachment.getMimeType()));
        }
    }
}
