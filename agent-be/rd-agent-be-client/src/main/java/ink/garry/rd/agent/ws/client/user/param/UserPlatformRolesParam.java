package ink.garry.rd.agent.ws.client.user.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 覆盖保存用户平台角色 HTTP 入参。
 */
@Data
public class UserPlatformRolesParam {

    @NotBlank(message = "用户编号不能为空")
    private String num;

    /** 平台角色 num 列表；可为空表示清空。 */
    private List<String> roleNums;
}
