package ink.garry.rd.agent.ws.client.user.dto;

import lombok.Data;

/**
 * 更新用户入参 DTO（application 边界）。
 */
@Data
public class UserUpdateParamDTO {

    private String num;
    private String username;
    private String email;
    private String remark;
}
