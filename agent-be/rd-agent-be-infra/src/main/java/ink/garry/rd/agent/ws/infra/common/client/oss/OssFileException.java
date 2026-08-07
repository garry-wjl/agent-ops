package ink.garry.rd.agent.ws.infra.common.client.oss;

import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import lombok.Getter;

/**
 * OSS 文件存储网关异常。
 * <p>
 * 继承 {@link BusinessException} 后可被 adapter 层全局
 * {@code GlobalExceptionHandler} 统一捕获并以 {@code Result.fail} 形态返回;
 * 业务侧可按 {@link #ossCode} 做差异化处理。
 *
 * <h3>errorCode 命名约定</h3>
 * 字符串字面量,定位调用点(便于日志检索 / Sentry 分组),例:
 * <ul>
 *   <li>{@code oss_upload_presign} — {@link OssClient#uploadPresign} catch 分支</li>
 *   <li>{@code oss_download_presign} — {@link OssClient#generateDownloadUrl} catch 分支</li>
 * </ul>
 *
 * <h3>ossCode</h3>
 * OSS / 上游服务返回的业务 code;本地异常(如签名缺失、轮询超时)统一为 {@code 0}。
 */
@Getter
public class OssFileException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** BizCode {@code THIRD_PARTY_ERROR} 数字编码(与 client.common.BizCode 同步)。 */
    private static final int CODE_THIRD_PARTY_ERROR = 9002;

    /** 本地错误标签,如 {@code oss_upload_presign} / {@code oss_download_presign}。 */
    private final String errorCode;

    /** OSS / 上游服务返回的业务 code;本地异常为 {@code 0}。 */
    private final int ossCode;

    /** OSS / 上游服务返回的 invokeId,排障必备;本地异常为 {@code null}。 */
    private final String invokeId;

    /**
     * 构造 OSS 异常。
     *
     * @param errorCode 本地错误标签
     * @param ossCode   OSS / 上游服务返回的业务 code
     * @param message   人类可读描述
     * @param invokeId  上游 invokeId,可为 null
     */
    public OssFileException(String errorCode, int ossCode, String message, String invokeId) {
        super(CODE_THIRD_PARTY_ERROR, errorCode + ": " + message
                + (invokeId == null ? "" : " (invokeId=" + invokeId + ")"));
        this.errorCode = errorCode;
        this.ossCode = ossCode;
        this.invokeId = invokeId;
    }
}
