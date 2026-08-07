package ink.garry.rd.agent.ws.infra.common.exception;

/**
 * 基础设施分布式锁异常。
 * <p>
 * 加锁/解锁失败、超时、被中断等场景抛出;由 application 层捕获后转 BusinessException。
 */
public class LockException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LockException(String message) {
        super(message);
    }

    public LockException(String message, Throwable cause) {
        super(message, cause);
    }
}
