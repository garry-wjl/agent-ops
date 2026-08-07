package ink.garry.rd.agent.ws.infra.common.client.cloudbus.dto;

import lombok.Data;

@Data
public class DepartmentDTO {
    /**
     * 部门编码
     */
    private String deptCode;

    /**
     * 部门名
     */
    private String name;
}
