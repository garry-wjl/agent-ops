package ink.garry.rd.agent.ws.client.common.employee;

import lombok.Data;

/**
 * 通用员工档案 Vo（员工搜索结果返回前端）。
 */
@Data
public class EmployeeProfileVO {

    /** 员工工号。 */
    private String empNo;

    /** 员工显示名（姓名）。 */
    private String displayName;

    /** 所属部门名称（可空）。 */
    private String dept;
}
