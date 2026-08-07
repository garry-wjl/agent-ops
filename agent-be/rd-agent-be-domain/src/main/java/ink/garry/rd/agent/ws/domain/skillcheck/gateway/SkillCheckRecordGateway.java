package ink.garry.rd.agent.ws.domain.skillcheck.gateway;

/**
 * SkillCheckRecord 业务编码生成网关。
 * <p>
 * 由 infra 层 {@code SkillCheckRecordGatewayImpl}（{@code @Component}）实现。
 */
public interface SkillCheckRecordGateway {

    /**
     * 生成 SkillCheckRecord 业务编号（前缀 SCR）。
     * <p>
     * 由 {@code SkillCheckRecord.save} 在 num 为空时调用；实现方需保证全局唯一。
     *
     * @return 全局唯一的检测记录编号（如 {@code SCR2026061012345}）
     */
    String generateCheckRecordNum();
}
