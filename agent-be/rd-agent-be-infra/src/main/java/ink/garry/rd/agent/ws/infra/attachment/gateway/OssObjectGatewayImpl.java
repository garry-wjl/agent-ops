package ink.garry.rd.agent.ws.infra.attachment.gateway;

import ink.garry.rd.agent.ws.domain.attachment.dto.OssPresignResult;
import ink.garry.rd.agent.ws.domain.attachment.gateway.OssObjectGateway;
import ink.garry.rd.agent.ws.infra.common.client.oss.OssClient;
import ink.garry.rd.agent.ws.infra.common.client.oss.dto.OssPresignResultDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * {@link OssObjectGateway} → {@link OssClient}。
 */
@Component
public class OssObjectGatewayImpl implements OssObjectGateway {

    @Resource
    private OssClient ossClient;

    @Override
    public OssPresignResult presignUpload(String fileName) {
        OssPresignResultDTO dto = ossClient.uploadPresign(fileName);
        return OssPresignResult.builder()
                .fileId(dto.getFileId())
                .url(dto.getUrl())
                .method(dto.getMethod())
                .expiration(dto.getExpiration())
                .signedHeaders(dto.getSignedHeaders())
                .build();
    }

    @Override
    public String generateDownloadUrl(String fileId) {
        return ossClient.generateDownloadUrl(fileId);
    }

    @Override
    public byte[] downloadBytes(String fileId) {
        return ossClient.downloadBytes(fileId);
    }
}
