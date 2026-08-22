package ink.garry.rd.agent.ws.application.auth.command;

import ink.garry.rd.agent.ws.application.auth.LoginSecurityProperties;
import ink.garry.rd.agent.ws.infra.common.constant.RedisKeyConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LoginFailCounterService} 单测。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginFailCounterServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private LoginSecurityProperties props;

    @InjectMocks
    private LoginFailCounterService service;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(props.getFailThreshold()).thenReturn(3);
        lenient().when(props.getFailTtlMinutes()).thenReturn(15);
    }

    @Test
    void requiresCaptcha_whenCountGeThreshold() {
        when(valueOps.get(RedisKeyConstant.AUTH_LOGIN_FAIL_PREFIX + "alice:1.2.3.4"))
                .thenReturn("3");
        assertTrue(service.requiresCaptcha("Alice", "1.2.3.4"));
    }

    @Test
    void requiresCaptcha_whenBelowThreshold() {
        when(valueOps.get(anyString())).thenReturn("2");
        assertFalse(service.requiresCaptcha("alice", "1.2.3.4"));
    }

    @Test
    void increment_setsValueWithTtl() {
        when(valueOps.get(anyString())).thenReturn("2");
        long n = service.increment("bob", "10.0.0.1");
        assertEquals(3L, n);
        verify(valueOps).set(
                eq(RedisKeyConstant.AUTH_LOGIN_FAIL_PREFIX + "bob:10.0.0.1"),
                eq("3"),
                eq(15L),
                eq(TimeUnit.MINUTES));
    }
}
