package ink.garry.rd.agent.ws.client.sandbox.vo;

import lombok.Data;

/**
 * 沙箱详情 Vo（adapter 层出参）。
 * <p>
 * 本期详情即沙箱全字段 + 当前状态，以嵌套 {@link SandboxVO} 承载，预留后续扩展（如运行指标）；
 * 由 application 的 {@code SandboxDetailDTO} 经 {@code SandboxVoAssembler} 转换而来。
 */
@Data
public class SandboxDetailVO {

    /** 沙箱全字段快照。 */
    private SandboxVO sandbox;
}
