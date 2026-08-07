package ink.garry.rd.agent.ws.client.session;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 删除会话入参。
 * <p>
 * 删除后会话不可恢复（含消息历史）；删除权限由后端按归属用户校验。
 */
@Data
public class SessionDeleteParam {
    /** 待删除会话业务编号（必填） */
    @NotBlank(message = "num 不能为空")
    private String num;
}
