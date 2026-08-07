package ink.garry.rd.agent.ws.client.session;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 会话重命名入参。
 * <p>
 * 仅修改标题；不影响消息历史。
 */
@Data
public class SessionRenameParam {
    /** 待重命名会话业务编号（必填） */
    @NotBlank(message = "num 不能为空")
    private String num;
    /** 新标题（必填） */
    @NotBlank(message = "newTitle 不能为空")
    private String newTitle;
}
