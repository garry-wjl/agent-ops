package ink.garry.rd.agent.ws.client.agent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * Agent 版本详情查询 DTO（对外单版本详情查询用；与 {@code AgentVersionDetailVO} 字段一一对应）。
 * <p>
 * 继承 {@link AgentVersionViewDTO}，额外携带 {@code snapshot}（兼容旧字段名）与 {@code configSnapshot}
 * 双份同源数据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentVersionDetailViewDTO extends AgentVersionViewDTO {

    /** 完整配置 snapshot（兼容旧字段名） */
    private Map<String, Object> snapshot;
    /** 完整配置 snapshot（与前端字段名对齐） */
    private Map<String, Object> configSnapshot;
}
