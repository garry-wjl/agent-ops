package ink.garry.rd.agent.ws.infra.attachment.repository;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.attachment.ChatAttachment;
import ink.garry.rd.agent.ws.domain.attachment.repository.ChatAttachmentRepository;
import ink.garry.rd.agent.ws.domain.attachment.valueobject.AttachmentKind;
import ink.garry.rd.agent.ws.infra.attachment.entity.ChatAttachmentEntity;
import ink.garry.rd.agent.ws.infra.attachment.mapper.ChatAttachmentMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

/**
 * {@link ChatAttachmentRepository} 实现。
 */
@Repository
public class ChatAttachmentRepositoryImpl implements ChatAttachmentRepository {

    @Resource
    private ChatAttachmentMapper chatAttachmentMapper;

    @Override
    public void save(ChatAttachment aggregate) {
        Assert.notNull(aggregate, "ChatAttachment 不能为 null");
        Assert.notBlank(aggregate.getNum(), "num 不能为空");
        Assert.notBlank(aggregate.getFileId(), "fileId 不能为空");

        ChatAttachmentEntity byFileId = chatAttachmentMapper.selectOne(new LambdaQueryWrapper<ChatAttachmentEntity>()
                .eq(ChatAttachmentEntity::getFileId, aggregate.getFileId()));
        if (byFileId != null) {
            aggregate.setId(byFileId.getId());
            aggregate.setNum(byFileId.getNum());
            return;
        }

        ChatAttachmentEntity entity = toEntity(aggregate);
        chatAttachmentMapper.insert(entity);
        aggregate.setId(entity.getId());
    }

    @Override
    public ChatAttachment findByNum(String num) {
        if (StrUtil.isBlank(num)) {
            return null;
        }
        ChatAttachmentEntity entity = chatAttachmentMapper.selectOne(new LambdaQueryWrapper<ChatAttachmentEntity>()
                .eq(ChatAttachmentEntity::getNum, num));
        return toDomain(entity);
    }

    @Override
    public ChatAttachment findByFileId(String fileId) {
        if (StrUtil.isBlank(fileId)) {
            return null;
        }
        ChatAttachmentEntity entity = chatAttachmentMapper.selectOne(new LambdaQueryWrapper<ChatAttachmentEntity>()
                .eq(ChatAttachmentEntity::getFileId, fileId));
        return toDomain(entity);
    }

    private ChatAttachmentEntity toEntity(ChatAttachment aggregate) {
        ChatAttachmentEntity entity = new ChatAttachmentEntity();
        entity.setId(aggregate.getId());
        entity.setNum(aggregate.getNum());
        entity.setWorkspaceNum(aggregate.getWorkspaceNum());
        entity.setFileId(aggregate.getFileId());
        entity.setFileName(aggregate.getFileName());
        entity.setMimeType(aggregate.getMimeType());
        entity.setSizeBytes(aggregate.getSizeBytes());
        entity.setKind(aggregate.getKind() != null ? aggregate.getKind().name() : null);
        entity.setAgentNum(aggregate.getAgentNum());
        entity.setCreateNo(aggregate.getCreateNo());
        entity.setUpdateNo(aggregate.getUpdateNo());
        entity.setCreateTime(aggregate.getCreateTime());
        entity.setUpdateTime(aggregate.getUpdateTime());
        entity.setDeleted(aggregate.getDeleted() != null ? aggregate.getDeleted() : 0);
        return entity;
    }

    private ChatAttachment toDomain(ChatAttachmentEntity entity) {
        if (entity == null) {
            return null;
        }
        ChatAttachment domain = new ChatAttachment();
        domain.setId(entity.getId());
        domain.setNum(entity.getNum());
        domain.setWorkspaceNum(entity.getWorkspaceNum());
        domain.setFileId(entity.getFileId());
        domain.setFileName(entity.getFileName());
        domain.setMimeType(entity.getMimeType());
        domain.setSizeBytes(entity.getSizeBytes());
        if (StrUtil.isNotBlank(entity.getKind())) {
            domain.setKind(AttachmentKind.valueOf(entity.getKind()));
        }
        domain.setAgentNum(entity.getAgentNum());
        domain.setCreateNo(entity.getCreateNo());
        domain.setUpdateNo(entity.getUpdateNo());
        domain.setCreateTime(entity.getCreateTime());
        domain.setUpdateTime(entity.getUpdateTime());
        domain.setDeleted(entity.getDeleted());
        return domain;
    }
}
