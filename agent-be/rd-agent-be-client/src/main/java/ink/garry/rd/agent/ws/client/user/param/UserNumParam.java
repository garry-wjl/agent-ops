package ink.garry.rd.agent.ws.client.user.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 仅含用户业务编号的写操作入参（启用 / 禁用等）。
 */
@Data
public class UserNumParam {

    @NotBlank(message = "用户编号不能为空")
    private String num;
}
