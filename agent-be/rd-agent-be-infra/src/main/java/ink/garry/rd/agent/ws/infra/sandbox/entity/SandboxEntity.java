package ink.garry.rd.agent.ws.infra.sandbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.sandbox.Sandbox;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxStatus;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 沙箱资产持久化实体（对应表 {@code sandbox}）。
 * <p>
 * 与 domain {@link Sandbox} 一一对应：枚举 {@code type} / {@code status} 以字符串列落库，
 * {@code cpu} 以 {@code DECIMAL(4,1)} 落库（0.5 步进）。transient 依赖
 * （Repository / Gateway / Publisher）由 {@code SandboxFactory} 装配，不在此映射。
 */
@Data
@TableName("sandbox")
public class SandboxEntity {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号（前缀 SBX，由 {@code SandboxGateway.generateSandboxNum} 经 BizNumGenerator 生成） */
    private String num;

    /** 归属工作空间业务编号 */
    @TableField("workspace_num")
    private String workspaceNum;

    /** 沙箱名称；同工作空间内唯一 */
    private String name;

    /** 沙箱类型：CODE=代码沙箱（对应 {@link SandboxType}） */
    private String type;

    /** CPU 核数（0.5 步进，区间 0.5~16），DECIMAL(4,1) */
    private BigDecimal cpu;

    /** 内存大小（MB，区间 128~65536） */
    @TableField("memory_mb")
    private Integer memoryMb;

    /** 容器存活时间（分钟，区间 1~1440） */
    @TableField("alive_minutes")
    private Integer aliveMinutes;

    /** 状态：DRAFT/INITIALIZED/ONLINE/OFFLINE/FAILED（对应 {@link SandboxStatus}） */
    private String status;

    /** 备注（≤100 字，可空） */
    private String remark;

    /** OpenSandbox 容器实例 id；草稿 / 失败态为空 */
    @TableField("sandbox_instance_id")
    private String sandboxInstanceId;

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
     * <p>type / status 字符串列反序列化为枚举；transient 依赖由调用方（SandboxFactory）装配。
     *
     * @param e MyBatis 查询出的实体
     * @return 领域聚合根；e 为 null 返回 null
     */
    public static Sandbox toDomain(SandboxEntity e) {
        if (e == null) {
            return null;
        }
        Sandbox s = new Sandbox();
        s.setId(e.getId());
        s.setNum(e.getNum());
        s.setWorkspaceNum(e.getWorkspaceNum());
        s.setName(e.getName());
        s.setType(e.getType() == null ? null : SandboxType.valueOf(e.getType()));
        s.setCpu(e.getCpu());
        s.setMemoryMb(e.getMemoryMb());
        s.setAliveMinutes(e.getAliveMinutes());
        s.setStatus(e.getStatus() == null ? null : SandboxStatus.valueOf(e.getStatus()));
        s.setRemark(e.getRemark());
        s.setSandboxInstanceId(e.getSandboxInstanceId());
        s.setCreateNo(e.getCreateNo());
        s.setUpdateNo(e.getUpdateNo());
        s.setDeleted(e.getDeleted());
        s.setCreateTime(e.getCreateTime());
        s.setUpdateTime(e.getUpdateTime());
        return s;
    }

    /**
     * Domain → Entity。
     * <p>枚举序列化为字符串列；deleted 为 null 时兜底 0（NOT NULL 列约束）。
     *
     * @param s 领域聚合根
     * @return MyBatis 持久化实体
     */
    public static SandboxEntity fromDomain(Sandbox s) {
        SandboxEntity e = new SandboxEntity();
        e.setId(s.getId());
        e.setNum(s.getNum());
        e.setWorkspaceNum(s.getWorkspaceNum());
        e.setName(s.getName());
        e.setType(s.getType() == null ? null : s.getType().name());
        e.setCpu(s.getCpu());
        e.setMemoryMb(s.getMemoryMb());
        e.setAliveMinutes(s.getAliveMinutes());
        e.setStatus(s.getStatus() == null ? null : s.getStatus().name());
        e.setRemark(s.getRemark());
        e.setSandboxInstanceId(s.getSandboxInstanceId());
        e.setCreateNo(s.getCreateNo());
        e.setUpdateNo(s.getUpdateNo());
        e.setDeleted(s.getDeleted() == null ? 0 : s.getDeleted());
        e.setCreateTime(s.getCreateTime());
        e.setUpdateTime(s.getUpdateTime());
        return e;
    }
}
