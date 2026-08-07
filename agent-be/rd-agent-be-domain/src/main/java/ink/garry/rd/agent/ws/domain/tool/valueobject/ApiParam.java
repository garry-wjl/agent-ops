package ink.garry.rd.agent.ws.domain.tool.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * API 参数值对象（贫血模型，FunctionCall 手动录入端点的 query / path 参数，详见 PRD §7.6）。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiParam {

    /** 参数名（合法变量名：字母 + 数字 + 下划线）；path 参数须与 path 占位符一一对应。 */
    private String name;

    /** 参数数据类型。 */
    private ApiParamType type;

    /** 默认值（可选）；字符串形式存储，运行时按 {@link #type} 反序列化。 */
    private String defaultValue;

    /** 描述（必填，≤200 字符；给 LLM 看的参数说明）。 */
    private String description;
}
