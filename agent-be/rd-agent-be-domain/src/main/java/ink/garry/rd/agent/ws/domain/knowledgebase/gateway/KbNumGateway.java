package ink.garry.rd.agent.ws.domain.knowledgebase.gateway;

/**
 * 知识库业务编号网关。
 */
public interface KbNumGateway {

    String generateKbNum();

    String generateFileNum();

    String generateTaskNum();
}
