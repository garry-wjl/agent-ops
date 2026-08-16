package ink.garry.rd.agent.ws.application.attachment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 附件上传与读取限制配置（{@code app.attachment.*}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.attachment")
public class AttachmentProperties {

    /** 单文件大小上限（字节），默认 10MB */
    private long maxSizeBytes = 10_485_760L;

    /** 单轮附件数量上限 */
    private int maxCountPerTurn = 6;

    /** read_attachment 默认截断字符数 */
    private int readMaxChars = 20_000;

    /** 允许的 MIME 白名单 */
    private List<String> allowedMimeTypes = new ArrayList<>(List.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/gif",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "text/markdown"
    ));
}
