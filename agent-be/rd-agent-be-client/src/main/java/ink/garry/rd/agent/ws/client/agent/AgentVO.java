package ink.garry.rd.agent.ws.client.agent;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 列表项 VO（v2.1 精简 + v2.2 加 agentSource）。
 * <p>
 * 字段裁剪原则：列表只保留管理员一眼能识别 Agent 的关键字段，详情字段下沉至 {@link AgentDetailVO}。
 * <p>
 * 派生字段说明：
 * <ul>
 *   <li>{@link #agentSource} 由 {@link #creationMode} 派生：CONFIG → MANUAL，A2A → NACOS（v2.2，不入库）</li>
 *   <li>{@link #skillNum} / {@link #skillNames} 按 creationMode 分支组装：
 *     CONFIG 取 currentVersion.configSnapshot.skillNums；A2A 取 a2aSource.remoteSkills</li>
 *   <li>{@link #updateTime} 对 A2A 即最近一次 Nacos 同步覆盖时间</li>
 * </ul>
 */
@Data
public class AgentVO {
    /** Agent 业务编号 AGT... */
    private String num;
    /** Agent 显示名 */
    private String name;
    /** Agent 描述（v2.1 列表新增，便于一眼识别用途） */
    private String description;
    /** 状态 DRAFT_ONLY / PUBLISHED / OFFLINE */
    private String status;
    /** Skill 数量：CONFIG 取 configSnapshot.skillNums.size()，A2A 取 a2aSource.remoteSkills.size() */
    private Integer skillNum;
    /** Skill 名称数组：CONFIG 反查 Skill 表名称，A2A 取 remoteSkills.name；用于列表 hover/标签展示 */
    private List<String> skillNames;
    /** 来源标识 MANUAL / NACOS（v2.2 派生字段，不入库；CONFIG → MANUAL，A2A → NACOS） */
    private String agentSource;
    /** 创建方式 CONFIG / A2A（保留：前端 CSS 类名 / 排序 / 跳转分流仍依赖原始 mode） */
    private String creationMode;
    /** 创建时间（v2.1 新增） */
    private LocalDateTime createTime;
    /** 最后更新时间；A2A 即最近 Nacos 同步覆盖时间 */
    private LocalDateTime updateTime;
}
