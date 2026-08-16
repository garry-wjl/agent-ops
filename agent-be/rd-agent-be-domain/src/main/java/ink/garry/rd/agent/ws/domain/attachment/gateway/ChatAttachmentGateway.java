package ink.garry.rd.agent.ws.domain.attachment.gateway;

/**
 * 附件业务编号生成网关。
 */
public interface ChatAttachmentGateway {

    /**
     * 生成附件业务编号（前缀 CHA）。
     *
     * @return 业务号
     */
    String generateChatAttachmentNum();
}
