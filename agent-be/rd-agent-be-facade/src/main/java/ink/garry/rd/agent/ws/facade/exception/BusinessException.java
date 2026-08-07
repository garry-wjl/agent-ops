package ink.garry.rd.agent.ws.facade.exception;

import lombok.Getter;

/**
 * 业务异常，由 application / domain 层抛出，
 * 由 adapter 层 GlobalExceptionHandler 统一拦截并返回 {@code Result.fail}。
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码，默认 9001 */
    private final Integer code;

    /**
     * 以默认错误码 9001 抛出业务异常。
     *
     * @param message 异常描述
     */
    public BusinessException(String message) {
        super(message);
        this.code = 9001;
    }

    /**
     * 以指定错误码抛出业务异常；code 为 null 时回落到 9001。
     *
     * @param code    业务错误码
     * @param message 异常描述
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code != null ? code : 9001;
    }

    /**
     * 以指定错误码与根因抛出业务异常；code 为 null 时回落到 9001。
     *
     * @param code    业务错误码
     * @param message 异常描述
     * @param cause   根因
     */
    public BusinessException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code != null ? code : 9001;
    }
}
