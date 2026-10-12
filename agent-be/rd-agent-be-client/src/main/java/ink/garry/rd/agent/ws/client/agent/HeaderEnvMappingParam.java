package ink.garry.rd.agent.ws.client.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建 / 编辑 Agent 时的请求头 → 沙箱环境变量映射入参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeaderEnvMappingParam {

    /** 入站请求头名（大小写不敏感）。 */
    private String headerName;

    /** 沙箱环境变量名。 */
    private String envName;
}
