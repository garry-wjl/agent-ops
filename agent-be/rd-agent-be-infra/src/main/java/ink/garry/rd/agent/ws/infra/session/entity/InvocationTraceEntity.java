package ink.garry.rd.agent.ws.infra.session.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.session.InvocationTrace;
import ink.garry.rd.agent.ws.domain.session.valueobject.InvocationStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("invocation_trace")
public class InvocationTraceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String num;
    @TableField("trace_id")
    private String traceId;
    @TableField("session_num")
    private String sessionNum;
    @TableField("agent_num")
    private String agentNum;
    @TableField("agent_version_num")
    private String agentVersionNum;
    @TableField("caller_user_id")
    private String callerUserId;
    @TableField("input_summary")
    private String inputSummary;
    @TableField("output_summary")
    private String outputSummary;
    @TableField("step_count")
    private Integer stepCount;
    @TableField("total_tokens")
    private Integer totalTokens;
    @TableField("total_latency_ms")
    private Integer totalLatencyMs;
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

    public static InvocationTrace toDomain(InvocationTraceEntity e) {
        if (e == null) {
            return null;
        }
        InvocationTrace t = new InvocationTrace();
        t.setId(e.getId());
        t.setNum(e.getNum());
        t.setTraceId(e.getTraceId());
        t.setSessionNum(e.getSessionNum());
        t.setAgentNum(e.getAgentNum());
        t.setAgentVersionNum(e.getAgentVersionNum());
        t.setCallerUserId(e.getCallerUserId());
        t.setInputSummary(e.getInputSummary());
        t.setOutputSummary(e.getOutputSummary());
        t.setStepCount(e.getStepCount());
        t.setTotalTokens(e.getTotalTokens());
        t.setTotalLatencyMs(e.getTotalLatencyMs());
        t.setStatus(InvocationStatus.valueOf(e.getStatus()));
        t.setCreateNo(e.getCreateNo());
        t.setUpdateNo(e.getUpdateNo());
        t.setDeleted(e.getDeleted());
        t.setCreateTime(e.getCreateTime());
        t.setUpdateTime(e.getUpdateTime());
        return t;
    }

    public static InvocationTraceEntity fromDomain(InvocationTrace t) {
        InvocationTraceEntity e = new InvocationTraceEntity();
        e.setId(t.getId());
        e.setNum(t.getNum());
        e.setTraceId(t.getTraceId());
        e.setSessionNum(t.getSessionNum());
        e.setAgentNum(t.getAgentNum());
        e.setAgentVersionNum(t.getAgentVersionNum());
        e.setCallerUserId(t.getCallerUserId());
        e.setInputSummary(t.getInputSummary());
        e.setOutputSummary(t.getOutputSummary());
        e.setStepCount(t.getStepCount());
        e.setTotalTokens(t.getTotalTokens());
        e.setTotalLatencyMs(t.getTotalLatencyMs());
        e.setStatus(t.getStatus().name());
        e.setCreateNo(t.getCreateNo());
        e.setUpdateNo(t.getUpdateNo());
        e.setDeleted(t.getDeleted() == null ? 0 : t.getDeleted());
        e.setCreateTime(t.getCreateTime());
        e.setUpdateTime(t.getUpdateTime());
        return e;
    }
}
