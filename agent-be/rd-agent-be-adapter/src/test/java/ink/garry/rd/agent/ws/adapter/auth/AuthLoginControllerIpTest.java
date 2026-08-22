package ink.garry.rd.agent.ws.adapter.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link AuthLoginController#resolveClientIp} 单测。
 */
class AuthLoginControllerIpTest {

    @Test
    void prefersForwardedForFirstHop() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");
        req.setRemoteAddr("127.0.0.1");
        assertEquals("203.0.113.1", AuthLoginController.resolveClientIp(req));
    }

    @Test
    void fallsBackToRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("192.168.1.8");
        assertEquals("192.168.1.8", AuthLoginController.resolveClientIp(req));
    }
}
