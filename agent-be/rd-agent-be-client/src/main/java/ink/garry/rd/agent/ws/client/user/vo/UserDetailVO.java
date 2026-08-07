package ink.garry.rd.agent.ws.client.user.vo;

import lombok.Data;

import java.util.List;

/**
 * 用户详情 VO。
 */
@Data
public class UserDetailVO {

    private String num;
    private String username;
    private String email;
    private String remark;
    private String status;
    private List<String> platformRoleNums;
}
