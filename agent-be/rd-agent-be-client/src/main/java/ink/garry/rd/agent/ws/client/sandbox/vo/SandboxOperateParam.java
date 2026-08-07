package ink.garry.rd.agent.ws.client.sandbox.vo;

import lombok.Data;

/**
 * 沙箱单编号操作入参 Vo（adapter 层用）。
 * <p>
 * 供 delete / submit / offline / reonline 四个仅需沙箱业务编号的 POST 接口共用
 * （沙箱管理技术方案 §7.2.1）。
 */
@Data
public class SandboxOperateParam {

    /** 沙箱业务编号（必填）。 */
    private String num;
}
