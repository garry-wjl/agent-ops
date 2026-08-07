package ink.garry.rd.agent.ws.facade.common;

import lombok.Getter;
import lombok.Setter;

/**
 * 统一响应结果。
 * 结构按用户决策：{ code, message, data, traceId }。
 *
 * @param <T> 业务数据类型
 */
@Getter
@Setter
public class Result<T> {

    /** 业务码，0 表示成功，其他为业务/系统错误 */
    private Integer code;
    /** 提示信息（本项目特例：字段名为 message，非规范示例 msg） */
    private String message;
    /** 业务数据；列表场景也置于此字段，本项目不单独定义 rows */
    private T data;
    /** 链路追踪 ID，便于跨服务排障 */
    private String traceId;

    /**
     * 返回不带数据的成功响应。
     *
     * @param <T> 业务数据类型
     * @return code=0 / message="success" / data=null
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 返回携带业务数据的成功响应。
     *
     * @param data 业务数据
     * @param <T>  业务数据类型
     * @return code=0 / message="success" / data
     */
    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    /**
     * 返回失败响应。
     *
     * @param code    业务/系统错误码
     * @param message 错误描述
     * @param <T>     业务数据类型
     * @return 仅含 code / message 的失败响应
     */
    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    /**
     * 链式设置 traceId。
     *
     * @param traceId 链路追踪 ID
     * @return 当前对象
     */
    public Result<T> withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}
