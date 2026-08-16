package ink.garry.rd.agent.ws.infra.attachment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.attachment.entity.ChatAttachmentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天附件 Mapper。
 */
@Mapper
public interface ChatAttachmentMapper extends BaseMapper<ChatAttachmentEntity> {
}
