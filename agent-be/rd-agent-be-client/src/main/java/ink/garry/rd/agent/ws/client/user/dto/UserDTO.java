package ink.garry.rd.agent.ws.client.user.dto;

import lombok.Data;

/**
 * 用户摘要 DTO（列表 / 创建返回）。
 */
@Data
public class UserDTO {

    private String num;
    private String username;
    private String email;
    private String remark;
    private String status;
}
