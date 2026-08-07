package ink.garry.rd.agent.ws.client.tool.vo;

import lombok.Data;

/**
 * API 参数入参 / 出参 Vo（adapter 层；对应 ApiParamDTO，用于 query / path 参数）。
 */
@Data
public class ApiParamVo {

    /** 参数名（合法变量名）。 */
    private String name;

    /** 参数数据类型：STRING / NUMBER / BOOLEAN / INTEGER。 */
    private String type;

    /** 默认值（可选，字符串形式）。 */
    private String defaultValue;

    /** 描述（≤200 字符）。 */
    private String description;
}
