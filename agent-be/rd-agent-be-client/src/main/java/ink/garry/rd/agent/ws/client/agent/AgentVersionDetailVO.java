package ink.garry.rd.agent.ws.client.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * Agent 版本详情 VO（含完整配置快照）。
 * <p>
 * v2.8：新增 {@link #configSnapshot} 字段与 {@link #snapshot} 并存，与前端 type 对齐
 * （前端 {@code currentVersion?.configSnapshot} 期望该名）；旧字段 {@code snapshot}
 * 保留兼容旧调用方。两者由 AgentQueryService 同时填充同一份数据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentVersionDetailVO extends AgentVersionVO {
    /** 完整配置 snapshot（兼容旧字段名）。新代码请使用 {@link #configSnapshot}。 */
    private Map<String, Object> snapshot;
    /** 完整配置 snapshot（v2.8 新增，与前端 type {@code configSnapshot} 字段名对齐）。 */
    private Map<String, Object> configSnapshot;
}
