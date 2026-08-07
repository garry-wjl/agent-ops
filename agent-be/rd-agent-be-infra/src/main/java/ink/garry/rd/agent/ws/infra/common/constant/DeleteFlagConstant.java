package ink.garry.rd.agent.ws.infra.common.constant;

/**
 * 逻辑删除标志常量。
 * <p>
 * 数据库 {@code is_deleted} 字段统一取值约定:
 * 与 MyBatis-Plus {@code @TableLogic} 默认 0/1 语义一致。
 */
public final class DeleteFlagConstant {

    /** 未删除 */
    public static final int NOT_DELETED = 0;

    /** 已删除(逻辑删除) */
    public static final int DELETED = 1;

    private DeleteFlagConstant() {
    }
}
