package ink.garry.rd.agent.ws.client.auth.login.dto;

import lombok.Data;

/**
 * 登录入参 DTO。
 */
@Data
public class LoginParamDTO {

    private String username;
    private String password;
    /** 客户端 IP（adapter 解析后注入） */
    private String clientIp;
    private String captchaId;
    private Integer slideX;
}
