package ink.garry.rd.agent.ws.domain.attachment.gateway;

import ink.garry.rd.agent.ws.domain.attachment.dto.OssPresignResult;

/**
 * OSS 对象网关：预签名上传 / 下载 URL / 拉取字节。
 */
public interface OssObjectGateway {

    /**
     * 申请上传预签名。
     *
     * @param fileName 原始文件名（可含路径段）
     * @return 预签名结果
     */
    OssPresignResult presignUpload(String fileName);

    /**
     * 生成短期下载 URL。
     *
     * @param fileId OSS 对象 ID
     * @return 签名 URL
     */
    String generateDownloadUrl(String fileId);

    /**
     * 下载对象字节。
     *
     * @param fileId OSS 对象 ID
     * @return 文件字节
     */
    byte[] downloadBytes(String fileId);
}
