package ink.garry.rd.agent.ws.infra.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台用户主数据实体（表 {@code sys_user}）。
 */
@Data
@TableName("sys_user")
public class UserEntity {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号 USR-xxx */
    private String num;

    /** 登录用户名（全局唯一） */
    private String username;

    /** 邮箱（全局唯一） */
    private String email;

    /** 备注 */
    private String remark;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** BCrypt 密码哈希 */
    @TableField("password_hash")
    private String passwordHash;

    /** 创建人编号 */
    @TableField("create_no")
    private String createNo;

    /** 更新人编号 */
    @TableField("update_no")
    private String updateNo;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 软删标记：0 未删，1 已删 */
    @TableLogic
    private Integer deleted;
}
