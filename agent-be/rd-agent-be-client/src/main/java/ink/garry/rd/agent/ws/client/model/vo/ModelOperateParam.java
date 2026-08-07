package ink.garry.rd.agent.ws.client.model.vo;

import lombok.Data;

/**
 * 模型单编号操作入参 Vo（adapter 层用）。
 * <p>
 * 供 delete / enable / disable 三个仅需模型业务编号的 POST 接口共用
 * （模型管理技术方案 §7.2.1）。
 */
@Data
public class ModelOperateParam {

    /** 模型业务编号（必填）。 */
    private String num;
}
