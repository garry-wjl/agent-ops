package ink.garry.rd.agent.ws.domain.attachment.repository;

import ink.garry.rd.agent.ws.domain.attachment.ChatAttachment;

/**
 * 聊天附件登记仓储。
 */
public interface ChatAttachmentRepository {

    /**
     * 持久化附件登记（仅 insert；业务禁止更新删除语义外的硬删）。
     *
     * @param aggregate 聚合
     */
    void save(ChatAttachment aggregate);

    /**
     * 按业务编号加载。
     *
     * @param num 业务号
     * @return 聚合；不存在返回 null
     */
    ChatAttachment findByNum(String num);

    /**
     * 按 OSS fileId 加载。
     *
     * @param fileId OSS 对象 ID
     * @return 聚合；不存在返回 null
     */
    ChatAttachment findByFileId(String fileId);
}
