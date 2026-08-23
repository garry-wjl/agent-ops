package ink.garry.rd.agent.ws.client.knowledgebase.constant;

/**
 * 知识库文件索引状态。
 */
public enum KbIndexStatus {
    PENDING,
    PARSING,
    CHUNKING,
    EMBEDDING,
    READY,
    FAILED
}
