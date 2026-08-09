package ink.garry.rd.agent.ws.client.user.param;

import lombok.Data;

/**
 * 用户分页查询 HTTP 入参。
 */
@Data
public class UserPageQueryParam {

    /** 关键字（匹配用户名 / 邮箱，可空）。 */
    private String keyword;

    /** 状态过滤：ENABLED / DISABLED，可空表示全部。 */
    private String status;

    /** 页码，从 1 开始。 */
    private Integer pageNo = 1;

    /** 每页条数。 */
    private Integer pageSize = 20;
}
