package ink.garry.rd.agent.ws.client.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 可调试版本 VO（adapter 返回）。
 * <p>
 * 由 {@code AgentQueryService.debugVersionList(agentNum)} 输出，供调试台「Agent 版本选择器」使用。
 * 覆盖草稿态（DRAFT）+ 当前在线（PUBLISHED）+ 历史（ARCHIVED）三态；前端据 {@link #statusLabel}
 * 标注「草稿态 / 发布态 / 历史态」，据 {@link #versionNum} 传回调试 invoke 的 {@code target_version}
 * （草稿态 versionNum 为 null，前端传字面量 {@code DRAFT}）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentDebugVersionVO {

    /** 版本号字符串（形如 v1.2.0）；草稿态为 null。 */
    private String versionNum;

    /** 版本状态：DRAFT / PUBLISHED / ARCHIVED。 */
    private String status;

    /** 状态中文标签：草稿态 / 发布态 / 历史态。 */
    private String statusLabel;

    /** 是否为当前在线版本（current_flag=1）。 */
    private boolean current;

    /** 发布时间；草稿态为 null。 */
    private LocalDateTime publishedTime;

    /** 发布备注；草稿态为 null。 */
    private String remark;
}
