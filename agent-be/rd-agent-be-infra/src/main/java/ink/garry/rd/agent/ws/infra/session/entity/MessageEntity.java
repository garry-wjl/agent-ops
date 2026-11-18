package ink.garry.rd.agent.ws.infra.session.entity;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.agent.valueobject.InputType;
import ink.garry.rd.agent.ws.domain.session.Message;
import ink.garry.rd.agent.ws.domain.session.valueobject.AssistantSegment;
import ink.garry.rd.agent.ws.domain.session.valueobject.MessageRole;
import ink.garry.rd.agent.ws.domain.session.valueobject.StepChain;
import ink.garry.rd.agent.ws.domain.session.valueobject.TurnTokenUsage;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("`message`")
public class MessageEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String num;
    @TableField("session_num")
    private String sessionNum;
    private String role;
    @TableField("input_type")
    private String inputType;
    private String content;
    @TableField("step_chain")
    private String stepChain;
    /**
     * 助手消息按到达顺序的段列表 JSON(List&lt;AssistantSegment&gt;)。
     * USER / TOOL 消息或旧消息为 null。
     */
    @TableField("segments_json")
    private String segmentsJson;
    /**
     * AgentScope Msg.content 原始 ContentBlock 列表 JSON。
     * 仅作 trace 导出 / 评测复用兜底,不返回给 FE。
     */
    @TableField("content_blocks_json")
    private String contentBlocksJson;
    @TableField("trace_id")
    private String traceId;
    /** 本轮 Agent run 编号。历史消息为空。 */
    @TableField("run_id")
    private String runId;
    /** 本轮输入 token，仅助手消息。 */
    @TableField("input_tokens")
    private Integer inputTokens;
    /** 本轮输出 token，仅助手消息。 */
    @TableField("output_tokens")
    private Integer outputTokens;
    /** 本轮缓存 token，仅助手消息。 */
    @TableField("cached_tokens")
    private Integer cachedTokens;
    /** 本轮总 token，仅助手消息。 */
    @TableField("total_tokens")
    private Integer totalTokens;
    @TableField("create_no")
    private String createNo;
    @TableField("update_no")
    private String updateNo;
    private Integer deleted;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;

    public static Message toDomain(MessageEntity e) {
        if (e == null) {
            return null;
        }
        Message m = new Message();
        m.setId(e.getId());
        m.setNum(e.getNum());
        m.setSessionNum(e.getSessionNum());
        m.setRole(MessageRole.valueOf(e.getRole()));
        m.setInputType(e.getInputType() == null ? null : InputType.valueOf(e.getInputType()));
        m.setContent(e.getContent());
        m.setStepChain(e.getStepChain() == null ? null : JSON.parseObject(e.getStepChain(), StepChain.class));
        m.setSegments(e.getSegmentsJson() == null ? null : JSON.parseArray(e.getSegmentsJson(), AssistantSegment.class));
        m.setContentBlocksJson(e.getContentBlocksJson());
        m.setTraceId(e.getTraceId());
        m.setRunId(e.getRunId());
        m.setTurnTokenUsage(toUsage(e));
        m.setCreateNo(e.getCreateNo());
        m.setUpdateNo(e.getUpdateNo());
        m.setDeleted(e.getDeleted());
        m.setCreateTime(e.getCreateTime());
        m.setUpdateTime(e.getUpdateTime());
        return m;
    }

    public static MessageEntity fromDomain(Message m) {
        MessageEntity e = new MessageEntity();
        e.setId(m.getId());
        e.setNum(m.getNum());
        e.setSessionNum(m.getSessionNum());
        e.setRole(m.getRole().name());
        e.setInputType(m.getInputType() == null ? null : m.getInputType().name());
        e.setContent(m.getContent());
        e.setStepChain(m.getStepChain() == null ? null : JSON.toJSONString(m.getStepChain()));
        List<AssistantSegment> segments = m.getSegments();
        e.setSegmentsJson(segments == null || segments.isEmpty() ? null : JSON.toJSONString(segments));
        e.setContentBlocksJson(m.getContentBlocksJson());
        e.setTraceId(m.getTraceId());
        e.setRunId(m.getRunId());
        applyUsage(e, m.getTurnTokenUsage());
        e.setCreateNo(m.getCreateNo());
        e.setUpdateNo(m.getUpdateNo());
        e.setDeleted(m.getDeleted() == null ? 0 : m.getDeleted());
        e.setCreateTime(m.getCreateTime());
        e.setUpdateTime(m.getUpdateTime());
        return e;
    }

    /** 四个用量字段都为空时不建对象，避免把「没上报」读成一轮 0 token。 */
    private static TurnTokenUsage toUsage(MessageEntity e) {
        if (e.getInputTokens() == null && e.getOutputTokens() == null
                && e.getCachedTokens() == null && e.getTotalTokens() == null) {
            return null;
        }
        return TurnTokenUsage.builder()
                .inputTokens(e.getInputTokens())
                .outputTokens(e.getOutputTokens())
                .cachedTokens(e.getCachedTokens())
                .totalTokens(e.getTotalTokens())
                .build();
    }

    private static void applyUsage(MessageEntity e, TurnTokenUsage usage) {
        if (usage == null) {
            return;
        }
        e.setInputTokens(usage.getInputTokens());
        e.setOutputTokens(usage.getOutputTokens());
        e.setCachedTokens(usage.getCachedTokens());
        e.setTotalTokens(usage.getTotalTokens());
    }
}
