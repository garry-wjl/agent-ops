package ink.garry.rd.agent.ws.client.user.dto;

import lombok.Data;

/**
 * 用户分页查询入参 DTO。
 */
@Data
public class UserPageQueryParamDTO {

    private String keyword;
    private String status;
    private Integer pageNo;
    private Integer pageSize;
}
