package ink.garry.rd.agent.ws.client.common;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询通用入参
 */
@Data
public class PageParam {
    @Min(value = 1, message = "pageNo 必须 ≥ 1")
    private Integer pageNo = 1;

    @Min(value = 1, message = "pageSize 必须 ≥ 1")
    private Integer pageSize = 20;
}
