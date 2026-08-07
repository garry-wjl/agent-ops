package ink.garry.rd.agent.ws.client.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * skillNums → skillRefs 存量刷数结果 VO（adapter 返回）。
 * <p>
 * 由一次性运维接口 {@code POST /agents/maintenance/migrate-skill-refs} 输出。
 * <b>临时代码</b>：本 VO 与整条迁移链路（Controller / Service / Mapper）在存量全量回填并校验通过后，
 * 下一个版本必须删除（详见技术方案 §13.1）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MigrateSkillRefsResultVO {

    /** 扫描到的快照总条数（agent_version 全部行 + agent 镜像行）。 */
    private int scanned;

    /** 本次实际回填 skillRefs 的快照条数。 */
    private int migrated;

    /** 跳过的快照条数（已含 skillRefs / 无 skillNums / A2A 无快照等）。 */
    private int skipped;
}
