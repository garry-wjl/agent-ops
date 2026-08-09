package ink.garry.rd.agent.ws.client.user.dto;

import lombok.Data;

/**
 * 重置密码入参 DTO。
 */
@Data
public class UserResetPasswordParamDTO {

    private String num;
    private String password;
}
