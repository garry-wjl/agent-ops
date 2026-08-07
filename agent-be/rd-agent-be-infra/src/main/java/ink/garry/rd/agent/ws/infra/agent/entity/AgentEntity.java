package ink.garry.rd.agent.ws.infra.agent.entity;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.agent.Agent;
import ink.garry.rd.agent.ws.domain.agent.valueobject.A2aSourceInfo;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentType;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.domain.agent.valueobject.CreationMode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 元信息持久化实体（对应表 agent）。
 * <p>
 * v2.0 起新增 {@code a2a_source} JSON 列与 {@code nacos_service_key} 冗余唯一键，
 * 用于 A2A Agent 的 Nacos 来源元数据持久化与幂等查询。
 */
@Data
@TableName("agent")
public class AgentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号 AGT... */
    private String num;

    /** Agent 名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 业务标签 JSON 数组（CONFIG / A2A 共用），由 fastjson2 序列化 List&lt;String&gt; */
    @TableField("tags")
    private String tags;

    /** 创建方式 CONFIG / A2A */
    @TableField("creation_mode")
    private String creationMode;

    /** 类型 NORMAL/SUPERVISOR/ROUTER */
    @TableField("agent_type")
    private String agentType;

    /** 负责人 userId */
    @TableField("owner_user_id")
    private String ownerUserId;

    /** 归属工作空间业务编号（前缀 WS-）；存量回填为 WS-DEFAULT */
    @TableField("workspace_num")
    private String workspaceNum;

    /** 状态 DRAFT_ONLY/PUBLISHED/OFFLINE */
    private String status;

    /** 当前在线版本号（仅 CONFIG） */
    @TableField("current_version_num")
    private String currentVersionNum;

    /**
     * v3.0：当前在线版本 ConfigSnapshot 镜像 JSON（仅 CONFIG）。
     * <p>
     * 发布事务内由 {@code agent.promotePublished} 同步写入；调试 / 评测 / 挂载下拉直接读，
     * 避免 join agent_version。fastjson2 序列化 {@link ConfigSnapshot}。
     */
    @TableField("config_snapshot")
    private String configSnapshot;

    /** 是否空白沙盒 Agent */
    private Integer sandbox;

    /** A2A 来源信息 JSON（仅 A2A），由 fastjson2 序列化 {@link A2aSourceInfo} */
    @TableField("a2a_source")
    private String a2aSource;

    /** A2A 幂等键 = nacosGroup@@nacosService（仅 A2A，UNIQUE 索引） */
    @TableField("nacos_service_key")
    private String nacosServiceKey;

    @TableField("create_no")
    private String createNo;

    @TableField("update_no")
    private String updateNo;

    private Integer deleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * Entity → Domain（不装配 transient 依赖）。
     *
     * @param e MyBatis 查询出的实体
     * @return 领域聚合根；e 为 null 返回 null
     */
    public static Agent toDomain(AgentEntity e) {
        if (e == null) {
            return null;
        }
        Agent a = new Agent();
        a.setId(e.getId());
        a.setNum(e.getNum());
        a.setName(e.getName());
        a.setDescription(e.getDescription());
        if (e.getTags() != null && !e.getTags().isEmpty()) {
            a.setTags(JSON.parseArray(e.getTags(), String.class));
        }
        a.setCreationMode(CreationMode.valueOf(e.getCreationMode()));
        a.setAgentType(AgentType.valueOf(e.getAgentType()));
        a.setOwnerUserId(e.getOwnerUserId());
        a.setWorkspaceNum(e.getWorkspaceNum());
        a.setStatus(AgentStatus.valueOf(e.getStatus()));
        a.setCurrentVersionNum(e.getCurrentVersionNum());
        if (e.getConfigSnapshot() != null && !e.getConfigSnapshot().isEmpty()) {
            a.setConfigSnapshot(JSON.parseObject(e.getConfigSnapshot(), ConfigSnapshot.class));
        }
        a.setSandbox(e.getSandbox() != null && e.getSandbox() == 1);
        if (e.getA2aSource() != null && !e.getA2aSource().isEmpty()) {
            a.setA2aSource(JSON.parseObject(e.getA2aSource(), A2aSourceInfo.class));
        }
        a.setNacosServiceKey(e.getNacosServiceKey());
        a.setCreateNo(e.getCreateNo());
        a.setUpdateNo(e.getUpdateNo());
        a.setDeleted(e.getDeleted());
        a.setCreateTime(e.getCreateTime());
        a.setUpdateTime(e.getUpdateTime());
        return a;
    }

    /**
     * Domain → Entity，准备写入 DB。
     *
     * @param a 领域聚合根
     * @return MyBatis 持久化实体
     */
    public static AgentEntity fromDomain(Agent a) {
        AgentEntity e = new AgentEntity();
        e.setId(a.getId());
        e.setNum(a.getNum());
        e.setName(a.getName());
        e.setDescription(a.getDescription());
        e.setTags(a.getTags() == null ? null : JSON.toJSONString(a.getTags()));
        e.setCreationMode(a.getCreationMode().name());
        e.setAgentType(a.getAgentType().name());
        e.setOwnerUserId(a.getOwnerUserId());
        e.setWorkspaceNum(a.getWorkspaceNum());
        e.setStatus(a.getStatus().name());
        e.setCurrentVersionNum(a.getCurrentVersionNum());
        e.setConfigSnapshot(a.getConfigSnapshot() == null ? null : JSON.toJSONString(a.getConfigSnapshot()));
        e.setSandbox(a.isSandbox() ? 1 : 0);
        e.setA2aSource(a.getA2aSource() == null ? null : JSON.toJSONString(a.getA2aSource()));
        e.setNacosServiceKey(a.getNacosServiceKey());
        e.setCreateNo(a.getCreateNo());
        e.setUpdateNo(a.getUpdateNo());
        e.setDeleted(a.getDeleted() == null ? 0 : a.getDeleted());
        e.setCreateTime(a.getCreateTime());
        e.setUpdateTime(a.getUpdateTime());
        return e;
    }

    /**
     * 通用 JSON util（避免在多实体重复定义）。
     *
     * @param obj 任意对象
     * @return JSON 字符串；obj 为 null 返回 null
     */
    public static String toJson(Object obj) {
        return obj == null ? null : JSON.toJSONString(obj);
    }
}
