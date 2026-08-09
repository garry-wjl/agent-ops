package ink.garry.rd.agent.ws.client.user.dto;

import lombok.Data;

/**
 * 创建用户入参 DTO（application 边界）。
 */
@Data
public class UserCreateParamDTO {

    private String username;
    private String email;
    private String remark;
    private String password;
}
