package ink.garry.rd.agent.ws.infra.session.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.session.Session;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`session`")
public class SessionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String num;
    @TableField("agent_num")
    private String agentNum;
    @TableField("agent_version_num")
    private String agentVersionNum;
    @TableField("skill_hint")
    private String skillHint;
    @TableField("creator_user_id")
    private String creatorUserId;
    private String title;
    @TableField("last_message_at")
    private LocalDateTime lastMessageAt;
    private String origin;
    @TableField("invoke_context")
    private String invokeContext;
    @TableField("create_no")
    private String createNo;
    @TableField("update_no")
    private String updateNo;
    private Integer deleted;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;

    public static Session toDomain(SessionEntity e) {
        if (e == null) {
            return null;
        }
        Session s = new Session();
        s.setId(e.getId());
        s.setNum(e.getNum());
        s.setAgentNum(e.getAgentNum());
        s.setAgentVersionNum(e.getAgentVersionNum());
        s.setSkillHint(e.getSkillHint());
        s.setCreatorUserId(e.getCreatorUserId());
        s.setTitle(e.getTitle());
        s.setLastMessageAt(e.getLastMessageAt());
        s.setOrigin(e.getOrigin());
        s.setInvokeContextJson(e.getInvokeContext());
        s.setCreateNo(e.getCreateNo());
        s.setUpdateNo(e.getUpdateNo());
        s.setDeleted(e.getDeleted());
        s.setCreateTime(e.getCreateTime());
        s.setUpdateTime(e.getUpdateTime());
        return s;
    }

    public static SessionEntity fromDomain(Session s) {
        SessionEntity e = new SessionEntity();
        e.setId(s.getId());
        e.setNum(s.getNum());
        e.setAgentNum(s.getAgentNum());
        e.setAgentVersionNum(s.getAgentVersionNum());
        e.setSkillHint(s.getSkillHint());
        e.setCreatorUserId(s.getCreatorUserId());
        e.setTitle(s.getTitle());
        e.setLastMessageAt(s.getLastMessageAt());
        e.setOrigin(s.getOrigin());
        e.setInvokeContext(s.getInvokeContextJson());
        e.setCreateNo(s.getCreateNo());
        e.setUpdateNo(s.getUpdateNo());
        e.setDeleted(s.getDeleted() == null ? 0 : s.getDeleted());
        e.setCreateTime(s.getCreateTime());
        e.setUpdateTime(s.getUpdateTime());
        return e;
    }
}
