package ink.garry.rd.agent.ws.domain.agent.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Agent 快照中的工具版本引用。
 * <p>当前 Tool 领域尚无独立版本表时，versionNum 可为空；具备版本能力后按该字段解析。</p>
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolRef {

    /** 工具业务编号。 */
    private String toolNum;

    /** 发布版本号。 */
    private String versionNum;
}
