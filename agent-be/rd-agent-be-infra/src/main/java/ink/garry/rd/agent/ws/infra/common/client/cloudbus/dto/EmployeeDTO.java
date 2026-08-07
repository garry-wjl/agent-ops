package ink.garry.rd.agent.ws.infra.common.client.cloudbus.dto;


import lombok.Data;

/**
 * 员工信息
 */
@Data
public class EmployeeDTO {

    /**
     * 在职
     */
    public static final String HR_STATUS_ON = "A";

    /**
     * 离职
     */
    public static final String HR_STATUS_OFF = "I";

    /**
     * 员工名
     */
    private String realName;

    /**
     * 员工ad
     */
    private String adName;

    /**
     * 员工email
     */
    private String email;

    /**
     * 人事状态（A=在职, I=离职）
     */
    private String hrStatus;
}
