package ink.garry.rd.agent.ws.infra.workspace.entity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.workspace.Workspace;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作空间持久化实体（对应表 {@code workspace}）。
 * <p>
 * 管理员 / 成员以工号字符串数组内联为 {@code admin_list} / {@code member_list} 两个 JSON 列
 * （仿 {@code SkillEntity.tags} 的 JSON-as-String + fastjson2 做法），与 domain {@link Workspace}
 * 的 {@code adminList} / {@code memberList} 一一对应；不另建成员子表。
 */
@Data
@TableName("workspace")
public class WorkspaceEntity {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号（前缀 WS-，由 {@code WorkspaceGateway.generateWorkspaceNum} 生成） */
    private String num;

    /** 空间名称 */
    private String name;

    /** 空间描述 */
    private String description;

    /** 管理员工号数组；持久化为 JSON 字符串列 admin_list，如 ["10001"] */
    @TableField("admin_list")
    private String adminList;

    /** 普通成员工号数组；持久化为 JSON 字符串列 member_list，如 ["10003"] */
    @TableField("member_list")
    private String memberList;

    /** 创建人工号 */
    @TableField("create_no")
    private String createNo;

    /** 更新人工号（兼任删除人语义） */
    @TableField("update_no")
    private String updateNo;

    /** 逻辑删除：0=正常 1=删除 */
    private Integer deleted;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间（兼任删除时间语义） */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * Entity → Domain。
     * <p>admin_list / member_list JSON 列反序列化为两个 {@code List<String>}；
     * transient 依赖（Repository / Gateway / Publisher）由调用方（WorkspaceFactory）装配。
     *
     * @param e MyBatis 查询出的实体
     * @return 领域聚合根；e 为 null 返回 null
     */
    public static Workspace toDomain(WorkspaceEntity e) {
        if (e == null) {
            return null;
        }
        Workspace w = new Workspace();
        w.setId(e.getId());
        w.setNum(e.getNum());
        w.setName(e.getName());
        w.setDescription(e.getDescription());
        w.setAdminList(parseList(e.getAdminList()));
        w.setMemberList(parseList(e.getMemberList()));
        w.setCreateNo(e.getCreateNo());
        w.setUpdateNo(e.getUpdateNo());
        w.setDeleted(e.getDeleted());
        w.setCreateTime(e.getCreateTime());
        w.setUpdateTime(e.getUpdateTime());
        return w;
    }

    /**
     * Domain → Entity。
     * <p>adminList / memberList 序列化为 JSON 字符串列（null 序列化为空数组，保证 NOT NULL 列约束）。
     *
     * @param w 领域聚合根
     * @return MyBatis 持久化实体
     */
    public static WorkspaceEntity fromDomain(Workspace w) {
        WorkspaceEntity e = new WorkspaceEntity();
        e.setId(w.getId());
        e.setNum(w.getNum());
        e.setName(w.getName());
        e.setDescription(w.getDescription());
        e.setAdminList(toJsonArray(w.getAdminList()));
        e.setMemberList(toJsonArray(w.getMemberList()));
        e.setCreateNo(w.getCreateNo());
        e.setUpdateNo(w.getUpdateNo());
        e.setDeleted(w.getDeleted() == null ? 0 : w.getDeleted());
        e.setCreateTime(w.getCreateTime());
        e.setUpdateTime(w.getUpdateTime());
        return e;
    }

    /** JSON 列 → {@code List<String>}；空值 / 非法 JSON 返回空列表。 */
    private static List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return new java.util.ArrayList<>();
        }
        try {
            List<String> list = JSON.parseObject(json, new TypeReference<List<String>>() {});
            return list == null ? new java.util.ArrayList<>() : list;
        } catch (Exception ignore) {
            return new java.util.ArrayList<>();
        }
    }

    /** {@code List<String>} → JSON 数组字符串；null 输出 {@code []}（NOT NULL 列兜底）。 */
    private static String toJsonArray(List<String> list) {
        return JSON.toJSONString(list == null ? new java.util.ArrayList<String>() : list);
    }
}
