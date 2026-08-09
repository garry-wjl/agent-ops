package ink.garry.rd.agent.ws.client.user.dto;

import lombok.Data;

import java.util.List;

/**
 * 保存平台角色入参 DTO。
 */
@Data
public class UserPlatformRolesParamDTO {

    private String num;
    private List<String> roleNums;
}
