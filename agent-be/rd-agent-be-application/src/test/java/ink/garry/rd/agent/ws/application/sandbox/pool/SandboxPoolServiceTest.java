package ink.garry.rd.agent.ws.application.sandbox.pool;

import ink.garry.rd.agent.ws.domain.sandbox.Sandbox;
import ink.garry.rd.agent.ws.domain.sandbox.factory.SandboxFactory;
import ink.garry.rd.agent.ws.domain.sandbox.gateway.SandboxContainerGateway;
import ink.garry.rd.agent.ws.domain.sandbox.gateway.SandboxGateway;
import ink.garry.rd.agent.ws.domain.sandbox.repository.SandboxRuntimeInstanceRepository;
import ink.garry.rd.agent.ws.domain.sandbox.repository.SandboxRuntimeInstanceRepository.SandboxRuntimeInstanceRecord;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxRuntimeStatus;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxStatus;
import ink.garry.rd.agent.ws.domain.session.Session;
import ink.garry.rd.agent.ws.domain.session.repository.SessionRepository;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SandboxPoolService} 单元测试（Mock 容器网关，不连 OpenSandbox）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SandboxPoolServiceTest {

    @Mock
    private SandboxFactory sandboxFactory;
    @Mock
    private SandboxGateway sandboxGateway;
    @Mock
    private SandboxContainerGateway sandboxContainerGateway;
    @Mock
    private SandboxRuntimeInstanceRepository runtimeRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rLock;

    @InjectMocks
    private SandboxPoolService sandboxPoolService;

    private final AtomicInteger sriSeq = new AtomicInteger(1);

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        // 看门狗：tryLock(wait, unit) 无 lease
        lenient().when(rLock.tryLock(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        // 兼容旧签名若误调用
        lenient().when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);
        lenient().when(sandboxGateway.generateRuntimeInstanceNum())
                .thenAnswer(inv -> "SRI" + sriSeq.getAndIncrement());
        lenient().when(sandboxContainerGateway.isAlive(anyString())).thenReturn(true);
    }

    @Test
    void provisionAsset_poolOff_createsNoContainer() {
        Sandbox asset = onlineAsset(false, 1, 8);
        when(sandboxFactory.buildSandboxByNum("SBX1")).thenReturn(asset);

        String rep = sandboxPoolService.provisionAsset("SBX1", "u1");

        assertEquals(null, rep);
        verify(sandboxContainerGateway, never()).create(any(), anyInt(), anyInt());
    }

    @Test
    void provisionAsset_poolOn_alsoCreatesNoContainer() {
        Sandbox asset = onlineAsset(true, 2, 8);
        when(sandboxFactory.buildSandboxByNum("SBX1")).thenReturn(asset);

        String rep = sandboxPoolService.provisionAsset("SBX1", "u1");

        assertEquals(null, rep);
        verify(sandboxContainerGateway, never()).create(any(), anyInt(), anyInt());
        verify(runtimeRepository, never()).insert(any());
    }

    @Test
    void ensureBound_reusesSameSession_andRenews() {
        Sandbox asset = onlineAsset(false, 1, 8);
        when(sandboxFactory.buildSandboxByNum("SBX1")).thenReturn(asset);
        SandboxRuntimeInstanceRecord bound = new SandboxRuntimeInstanceRecord(
                "SRI1", "SBX1", "WS1", "os-bound", SandboxRuntimeStatus.BOUND,
                "SES1", LocalDateTime.now(), null, "u1", "u1");
        when(runtimeRepository.findBoundBySessionNum("SES1")).thenReturn(Optional.of(bound));

        String id = sandboxPoolService.ensureBound("SBX1", "SES1", "u1");

        assertEquals("os-bound", id);
        verify(sandboxContainerGateway, never()).create(any(), anyInt(), anyInt());
        verify(runtimeRepository).update(any());
        verify(sandboxContainerGateway).renew(eq("os-bound"), eq(30));
    }

    @Test
    void ensureBound_ignoresIdleAndCreatesFresh() {
        Sandbox asset = onlineAsset(true, 1, 8);
        when(sandboxFactory.buildSandboxByNum("SBX1")).thenReturn(asset);
        when(runtimeRepository.findBoundBySessionNum("SES2")).thenReturn(Optional.empty());
        when(runtimeRepository.countAlive("SBX1")).thenReturn(0L);
        when(sandboxContainerGateway.create(any(), anyInt(), anyInt())).thenReturn("os-new");

        String id = sandboxPoolService.ensureBound("SBX1", "SES2", "u1");

        assertEquals("os-new", id);
        verify(runtimeRepository, never()).listBySandboxAndStatus(anyString(), any());
        ArgumentCaptor<SandboxRuntimeInstanceRecord> cap =
                ArgumentCaptor.forClass(SandboxRuntimeInstanceRecord.class);
        verify(runtimeRepository).insert(cap.capture());
        assertEquals(SandboxRuntimeStatus.BOUND, cap.getValue().status());
        assertEquals("SES2", cap.getValue().sessionNum());
        verify(sandboxContainerGateway).renew(eq("os-new"), eq(30));
    }

    @Test
    void ensureBound_createsWhenEmpty() {
        Sandbox asset = onlineAsset(true, 1, 8);
        when(sandboxFactory.buildSandboxByNum("SBX1")).thenReturn(asset);
        when(runtimeRepository.findBoundBySessionNum("SES2")).thenReturn(Optional.empty());
        when(runtimeRepository.countAlive("SBX1")).thenReturn(0L);
        when(sandboxContainerGateway.create(any(), anyInt(), anyInt()))
                .thenReturn("os-new");

        String id = sandboxPoolService.ensureBound("SBX1", "SES2", "u1");

        assertEquals("os-new", id);
        ArgumentCaptor<SandboxRuntimeInstanceRecord> cap =
                ArgumentCaptor.forClass(SandboxRuntimeInstanceRecord.class);
        verify(runtimeRepository).insert(cap.capture());
        assertEquals(SandboxRuntimeStatus.BOUND, cap.getValue().status());
        assertEquals("SES2", cap.getValue().sessionNum());
    }

    @Test
    void ensureBound_hitsMaxConcurrent_throws() {
        Sandbox asset = onlineAsset(false, 1, 1);
        when(sandboxFactory.buildSandboxByNum("SBX1")).thenReturn(asset);
        when(runtimeRepository.findBoundBySessionNum("SES3")).thenReturn(Optional.empty());
        when(runtimeRepository.countAlive("SBX1")).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> sandboxPoolService.ensureBound("SBX1", "SES3", "u1"));
        assertTrue(ex.getMessage().contains("上限"));
    }

    @Test
    void ensureBound_sameSessionConcurrent_createsOnce() throws Exception {
        Sandbox asset = onlineAsset(false, 1, 8);
        when(sandboxFactory.buildSandboxByNum("SBX1")).thenReturn(asset);
        when(runtimeRepository.findBoundBySessionNum("SES-MERGE")).thenReturn(Optional.empty());
        when(runtimeRepository.countAlive("SBX1")).thenReturn(0L);
        when(sandboxContainerGateway.create(any(), anyInt(), anyInt())).thenAnswer(inv -> {
            Thread.sleep(200);
            return "os-merged";
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<String> f1 = pool.submit(() -> {
            start.await();
            return sandboxPoolService.ensureBound("SBX1", "SES-MERGE", "u1");
        });
        Future<String> f2 = pool.submit(() -> {
            start.await();
            return sandboxPoolService.ensureBound("SBX1", "SES-MERGE", "u2");
        });
        start.countDown();
        assertEquals("os-merged", f1.get(5, TimeUnit.SECONDS));
        assertEquals("os-merged", f2.get(5, TimeUnit.SECONDS));
        pool.shutdownNow();
        verify(sandboxContainerGateway, times(1)).create(any(), anyInt(), anyInt());
    }

    @Test
    void markSessionActive_touchesAndRenews() {
        Sandbox asset = onlineAsset(false, 1, 8);
        when(sandboxFactory.buildSandboxByNum("SBX1")).thenReturn(asset);
        SandboxRuntimeInstanceRecord bound = new SandboxRuntimeInstanceRecord(
                "SRI1", "SBX1", "WS1", "os-1", SandboxRuntimeStatus.BOUND,
                "SES1", LocalDateTime.now().minusMinutes(5), null, "u1", "u1");
        when(runtimeRepository.findBoundBySessionNum("SES1")).thenReturn(Optional.of(bound));

        sandboxPoolService.markSessionActive("SES1");

        verify(runtimeRepository).update(any());
        verify(sandboxContainerGateway).renew(eq("os-1"), eq(30));
    }

    @Test
    void ensureAliveOrRebind_dead_rebuilds() {
        Sandbox asset = onlineAsset(false, 1, 8);
        when(sandboxFactory.buildSandboxByNum("SBX1")).thenReturn(asset);
        SandboxRuntimeInstanceRecord dead = new SandboxRuntimeInstanceRecord(
                "SRI1", "SBX1", "WS1", "os-dead", SandboxRuntimeStatus.BOUND,
                "SES1", LocalDateTime.now(), null, "u1", "u1");
        when(runtimeRepository.findBoundBySessionNum("SES1"))
                .thenReturn(Optional.of(dead))
                .thenReturn(Optional.empty());
        when(sandboxContainerGateway.isAlive("os-dead")).thenReturn(false);
        when(runtimeRepository.countAlive("SBX1")).thenReturn(0L);
        when(sandboxContainerGateway.create(any(), anyInt(), anyInt())).thenReturn("os-new");
        Session session = new Session();
        session.setNum("SES1");
        session.setAgentNum("AGT1");
        when(sessionRepository.findByNum("SES1")).thenReturn(session);

        String id = sandboxPoolService.ensureAliveOrRebind(
                "SES1", "os-dead", "SBX1", "AGT1", "u1");

        assertEquals("os-new", id);
        verify(sandboxContainerGateway).kill("os-dead");
        verify(runtimeRepository).softDelete("SRI1");
        verify(sandboxContainerGateway).create(any(), anyInt(), anyInt());
    }

    @Test
    void releaseBySession_alwaysKills_noIdleReturn() {
        Sandbox asset = onlineAsset(true, 2, 8);
        when(sandboxFactory.buildSandboxByNum("SBX1")).thenReturn(asset);
        SandboxRuntimeInstanceRecord bound = new SandboxRuntimeInstanceRecord(
                "SRI1", "SBX1", "WS1", "os-1", SandboxRuntimeStatus.BOUND,
                "SES1", LocalDateTime.now(), null, "u1", "u1");
        when(runtimeRepository.findBoundBySessionNum("SES1")).thenReturn(Optional.of(bound));

        sandboxPoolService.releaseBySession("SES1", "u1");

        verify(sandboxContainerGateway).kill("os-1");
        verify(runtimeRepository).softDelete("SRI1");
        verify(runtimeRepository, never()).update(any());
    }

    @Test
    void releaseBySession_poolOff_kills() {
        Sandbox asset = onlineAsset(false, 1, 8);
        when(sandboxFactory.buildSandboxByNum("SBX1")).thenReturn(asset);
        SandboxRuntimeInstanceRecord bound = new SandboxRuntimeInstanceRecord(
                "SRI1", "SBX1", "WS1", "os-1", SandboxRuntimeStatus.BOUND,
                "SES1", LocalDateTime.now(), null, "u1", "u1");
        when(runtimeRepository.findBoundBySessionNum("SES1")).thenReturn(Optional.of(bound));

        sandboxPoolService.releaseBySession("SES1", "u1");

        verify(sandboxContainerGateway).kill("os-1");
        verify(runtimeRepository).softDelete("SRI1");
    }

    @Test
    void cleanupDeadInstances_removesDead() {
        SandboxRuntimeInstanceRecord alive = new SandboxRuntimeInstanceRecord(
                "SRI-A", "SBX1", "WS1", "os-alive", SandboxRuntimeStatus.IDLE,
                null, LocalDateTime.now(), LocalDateTime.now(), "u1", "u1");
        SandboxRuntimeInstanceRecord dead = new SandboxRuntimeInstanceRecord(
                "SRI-D", "SBX1", "WS1", "os-dead", SandboxRuntimeStatus.BOUND,
                "SES-X", LocalDateTime.now(), null, "u1", "u1");
        when(runtimeRepository.listBySandbox("SBX1")).thenReturn(List.of(alive, dead));
        when(sandboxContainerGateway.isAlive("os-alive")).thenReturn(true);
        when(sandboxContainerGateway.isAlive("os-dead")).thenReturn(false);

        int cleaned = sandboxPoolService.cleanupDeadInstances("SBX1");

        assertEquals(1, cleaned);
        verify(runtimeRepository).softDelete("SRI-D");
        verify(sandboxContainerGateway).kill("os-dead");
        verify(runtimeRepository, never()).softDelete("SRI-A");
    }

    @Test
    void ensureBound_sessionIsolation_skipsIdleAndMountsPrefix() {
        when(sandboxContainerGateway.isolatesWorkspaceBySession()).thenReturn(true);
        Sandbox asset = onlineAsset(true, 1, 8);
        when(sandboxFactory.buildSandboxByNum("SBX1")).thenReturn(asset);
        when(runtimeRepository.findBoundBySessionNum("SES9")).thenReturn(Optional.empty());
        when(runtimeRepository.countAlive("SBX1")).thenReturn(0L);
        when(sandboxContainerGateway.create(any(), anyInt(), anyInt(), eq("WS1/AGT9/SES9")))
                .thenReturn("os-iso");

        String id = sandboxPoolService.ensureBound("SBX1", "SES9", "u1", "AGT9");

        assertEquals("os-iso", id);
        verify(runtimeRepository, never()).listBySandboxAndStatus(anyString(), any());
        verify(sandboxContainerGateway, never()).create(any(), anyInt(), anyInt());
        verify(sandboxContainerGateway, atLeastOnce()).renew(eq("os-iso"), anyInt());
    }

    private static Sandbox onlineAsset(boolean pool, int poolSize, int max) {
        Sandbox s = new Sandbox();
        s.setNum("SBX1");
        s.setWorkspaceNum("WS1");
        s.setCpu(BigDecimal.ONE);
        s.setMemoryMb(2048);
        s.setAliveMinutes(30);
        s.setStatus(SandboxStatus.ONLINE);
        s.setPoolEnabled(pool);
        s.setPoolSize(poolSize);
        s.setMaxConcurrent(max);
        s.setSessionIdleTtlMinutes(10);
        return s;
    }
}
