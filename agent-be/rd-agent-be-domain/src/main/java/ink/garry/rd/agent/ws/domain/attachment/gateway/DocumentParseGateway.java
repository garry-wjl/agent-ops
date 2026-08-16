package ink.garry.rd.agent.ws.domain.attachment.gateway;

/**
 * 文档解析网关：从 OSS 拉取并抽取纯文本（截断）。
 */
public interface DocumentParseGateway {

    /**
     * 抽取文档文本并截断至 maxChars。
     *
     * @param fileId   OSS 对象 ID
     * @param mimeType MIME
     * @param maxChars 最大字符数
     * @return 纯文本；不支持类型时抛业务异常或返回可读错误由调用方约定
     */
    String extractText(String fileId, String mimeType, int maxChars);
}
