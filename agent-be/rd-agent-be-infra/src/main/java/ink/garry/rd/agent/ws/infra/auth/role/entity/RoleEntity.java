package ink.garry.rd.agent.ws.infra.auth.role.entity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.auth.RoleScope;
import ink.garry.rd.agent.ws.domain.auth.role.Role;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色持久化实体（对应表 {@code role}）。
 * <p>permission_codes 列以 JSON 字符串存储，与 SkillEntity.tags 同范式。</p>
 */
@Data
@TableName("role")
public class RoleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String num;

    private String name;

    private String description;

    private String scope;

    @TableField("workspace_num")
    private String workspaceNum;

    private Integer builtin;

    @TableField("permission_codes")
    private String permissionCodes;

    private String status;

    @TableField("create_no")
    private String createNo;

    @TableField("update_no")
    private String updateNo;

    private Integer deleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    /** Entity → Domain。 */
    public static Role toDomain(RoleEntity e) {
        if (e == null) {
            return null;
        }
        Role r = new Role();
        r.setId(e.getId());
        r.setNum(e.getNum());
        r.setName(e.getName());
        r.setDescription(e.getDescription());
        r.setScope(e.getScope() == null ? null : RoleScope.valueOf(e.getScope()));
        r.setWorkspaceNum(e.getWorkspaceNum());
        r.setBuiltin(e.getBuiltin() != null && e.getBuiltin() == 1);
        r.setPermissionCodes(parseCodes(e.getPermissionCodes()));
        r.setStatus(e.getStatus());
        r.setCreateNo(e.getCreateNo());
        r.setUpdateNo(e.getUpdateNo());
        r.setDeleted(e.getDeleted());
        r.setCreateTime(e.getCreateTime());
        r.setUpdateTime(e.getUpdateTime());
        return r;
    }

    /** Domain → Entity。 */
    public static RoleEntity fromDomain(Role r) {
        RoleEntity e = new RoleEntity();
        e.setId(r.getId());
        e.setNum(r.getNum());
        e.setName(r.getName());
        e.setDescription(r.getDescription());
        e.setScope(r.getScope() == null ? null : r.getScope().name());
        e.setWorkspaceNum(r.getWorkspaceNum());
        e.setBuiltin(Boolean.TRUE.equals(r.getBuiltin()) ? 1 : 0);
        e.setPermissionCodes(toJsonArray(r.getPermissionCodes()));
        e.setStatus(r.getStatus() == null ? "ENABLED" : r.getStatus());
        e.setCreateNo(r.getCreateNo());
        e.setUpdateNo(r.getUpdateNo());
        e.setDeleted(r.getDeleted() == null ? 0 : r.getDeleted());
        e.setCreateTime(r.getCreateTime());
        e.setUpdateTime(r.getUpdateTime());
        return e;
    }

    private static Set<String> parseCodes(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashSet<>();
        }
        try {
            List<String> list = JSON.parseObject(json, new TypeReference<List<String>>() {});
            return list == null ? new LinkedHashSet<>() : new LinkedHashSet<>(list);
        } catch (Exception ignore) {
            return new LinkedHashSet<>();
        }
    }

    private static String toJsonArray(Set<String> codes) {
        return JSON.toJSONString(codes == null ? new ArrayList<String>() : new ArrayList<>(codes));
    }
}
