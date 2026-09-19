package ink.garry.rd.agent.ws.domain.session;

import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会话归属校验：错误身份须抛 BusinessException(1003)，供 SSE 全局异常处理返回 JSON Result。
 */
class SessionOwnerGuardTest {

    @Test
    void rename_rejectsNonOwner_withForbiddenBusinessException() {
        Session session = new Session();
        session.setCreatorUserId("USR-owner");
        session.setTitle("demo");

        assertThatThrownBy(() -> session.rename("hacked", "USR-other"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限操作该会话")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(1003);
    }
}
