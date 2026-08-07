package ink.garry.rd.agent.ws.client.common.oss;

import lombok.Data;

import java.util.Map;

/**
 * OSS 文件访问 URL VO。
 */
@Data
public class OssFileAccessUrlVO {

    /**
     * 源文件下载 / 直链。
     */
    private String url;
}
