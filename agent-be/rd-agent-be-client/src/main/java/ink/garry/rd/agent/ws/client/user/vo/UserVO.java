package ink.garry.rd.agent.ws.client.user.vo;

import lombok.Data;

/**
 * 用户摘要 VO。
 */
@Data
public class UserVO {

    private String num;
    private String username;
    private String email;
    private String remark;
    private String status;
}
