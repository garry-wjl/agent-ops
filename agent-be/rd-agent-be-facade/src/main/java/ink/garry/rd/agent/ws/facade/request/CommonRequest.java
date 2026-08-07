package ink.garry.rd.agent.ws.facade.request;

import lombok.Data;

/**
 * 通用请求基类，所有请求参数类可继承此类。
 */
@Data
public class CommonRequest {
    /** 操作人 userId（由 adapter 层从 UserContext 注入） */
    private String operatorId;
}
