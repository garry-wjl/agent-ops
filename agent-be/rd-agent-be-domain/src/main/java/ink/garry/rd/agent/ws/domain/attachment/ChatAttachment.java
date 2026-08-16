package ink.garry.rd.agent.ws.domain.attachment;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.attachment.gateway.ChatAttachmentGateway;
import ink.garry.rd.agent.ws.domain.attachment.repository.ChatAttachmentRepository;
import ink.garry.rd.agent.ws.domain.attachment.valueobject.AttachmentKind;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

/**
 * 聊天附件登记聚合根。
 * <p>
 * 上传成功后登记 fileId；业务上永不删除 OSS 对象与本行记录。
 */
@Getter
@Setter
public class ChatAttachment extends DomainEntity {

    /** 业务编号（前缀 CHA） */
    private String num;

    /** 工作空间编号 */
    private String workspaceNum;

    /** OSS 对象 ID，全局唯一 */
    private String fileId;

    /** 原始文件名 */
    private String fileName;

    /** MIME */
    private String mimeType;

    /** 字节大小 */
    private Long sizeBytes;

    /** IMAGE / FILE */
    private AttachmentKind kind;

    /** 关联 Agent，可空 */
    private String agentNum;

    private transient ChatAttachmentRepository chatAttachmentRepository;
    private transient ChatAttachmentGateway chatAttachmentGateway;

    public ChatAttachment() {
    }

    public ChatAttachment(String workspaceNum,
                          String fileId,
                          String fileName,
                          String mimeType,
                          Long sizeBytes,
                          AttachmentKind kind,
                          String agentNum,
                          ChatAttachmentRepository chatAttachmentRepository,
                          ChatAttachmentGateway chatAttachmentGateway) {
        this.workspaceNum = workspaceNum;
        this.fileId = fileId;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.kind = kind;
        this.agentNum = agentNum;
        this.chatAttachmentRepository = chatAttachmentRepository;
        this.chatAttachmentGateway = chatAttachmentGateway;
    }

    /**
     * 断言当前工作空间可访问该附件。
     *
     * @param workspaceNum 调用方工作空间
     */
    public void assertAccessible(String workspaceNum) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        if (!StrUtil.equals(this.workspaceNum, workspaceNum)) {
            throw new BusinessException(4103, "无权访问该附件");
        }
    }

    @Override
    public void domainValidate() {
        Assert.notBlank(num, "附件业务编号不能为空");
        Assert.notBlank(workspaceNum, "工作空间不能为空");
        Assert.notBlank(fileId, "fileId 不能为空");
        Assert.notBlank(fileName, "fileName 不能为空");
        Assert.notBlank(mimeType, "mimeType 不能为空");
        Assert.notNull(sizeBytes, "sizeBytes 不能为空");
        Assert.isTrue(sizeBytes >= 0, "sizeBytes 不能为负");
        Assert.notNull(kind, "kind 不能为空");
    }

    @Override
    public void save(String operatorId) {
        initialize(operatorId);
        if (StrUtil.isBlank(num)) {
            Assert.notNull(chatAttachmentGateway, "ChatAttachmentGateway 未装配");
            num = chatAttachmentGateway.generateChatAttachmentNum();
        }
        validate();
        Assert.notNull(chatAttachmentRepository, "ChatAttachmentRepository 未装配");
        chatAttachmentRepository.save(this);
    }

    /**
     * 禁止删除：OSS 与登记行永不删除。
     *
     * @param operatorId 操作人（忽略）
     */
    @Override
    public void delete(String operatorId) {
        throw new BusinessException(4107, "附件不可删除");
    }
}
