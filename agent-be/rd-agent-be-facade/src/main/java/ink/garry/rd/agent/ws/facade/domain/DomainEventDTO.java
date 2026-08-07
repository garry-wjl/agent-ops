package ink.garry.rd.agent.ws.facade.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 领域事件传输对象
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class DomainEventDTO {
    /** 事件唯一 ID */
    private String id;
    /** 事件类型常量（见 domain.common.DomainEventConstant） */
    private String type;
    /** 事件载荷（聚合实体或 POJO） */
    private Object data;
    /** 发生时间戳（ms） */
    private Long time;
    /** 操作人 userId */
    private String sender;
}
