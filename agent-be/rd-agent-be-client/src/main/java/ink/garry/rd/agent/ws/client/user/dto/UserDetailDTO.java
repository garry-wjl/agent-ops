package ink.garry.rd.agent.ws.client.user.dto;

import lombok.Data;

import java.util.List;

/**
 * 用户详情 DTO（含平台角色）。
 */
@Data
public class UserDetailDTO {

    private String num;
    private String username;
    private String email;
    private String remark;
    private String status;
    private List<String> platformRoleNums;
}
