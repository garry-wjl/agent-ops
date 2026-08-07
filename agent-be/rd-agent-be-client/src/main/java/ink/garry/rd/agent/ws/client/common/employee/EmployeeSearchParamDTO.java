package ink.garry.rd.agent.ws.client.common.employee;

import lombok.Data;

/**
 * 通用员工搜索入参 DTO（application 层边界；adapter 由 Query 参数转换而来）。
 */
@Data
public class EmployeeSearchParamDTO {

    /** 搜索关键字（工号或姓名，长度 ≥ 2）。 */
    private String keyword;

    /** 返回条数（默认 20，最大 50）。 */
    private Integer limit;
}
