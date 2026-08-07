package ink.garry.rd.agent.ws.client.common.employee;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用员工档案 DTO（application 层边界返回）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeProfileDTO {

    /** 员工工号。 */
    private String empNo;

    /** 员工显示名（姓名）。 */
    private String displayName;

    /** 所属部门名称（可空）。 */
    private String dept;
}
