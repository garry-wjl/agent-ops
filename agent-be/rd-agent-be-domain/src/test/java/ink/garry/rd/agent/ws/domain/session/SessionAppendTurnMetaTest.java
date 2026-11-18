package ink.garry.rd.agent.ws.domain.session;

import ink.garry.rd.agent.ws.domain.agent.valueobject.InputType;
import ink.garry.rd.agent.ws.domain.session.gateway.SessionNumGateway;
import ink.garry.rd.agent.ws.domain.session.repository.MessageRepository;
import ink.garry.rd.agent.ws.domain.session.repository.SessionRepository;
import ink.garry.rd.agent.ws.domain.session.valueobject.TurnTokenUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 一轮运行落库时，用户消息记下 Run ID，助手消息记下 Run ID 和汇总 Token。
 */
class SessionAppendTurnMetaTest {

    @Test
    void append_persistsRunIdAndTokenUsage() {
        List<Message> saved = new ArrayList<>();
        AtomicInteger seq = new AtomicInteger();
        Session session = new Session();
        session.setNum("SES-1");
        session.setCreatorUserId("alice");
        session.setTitle("已有标题");
        session.setSessionRepository(new SessionRepository() {
            @Override
            public void save(Session aggregate) {
            }

            @Override
            public Session findByNum(String num) {
                return null;
            }

            @Override
            public void deleteByNum(String num) {
            }
        });
        session.setSessionNumGateway(new SessionNumGateway() {
            @Override
            public String generateSessionNum() {
                return "SES-X";
            }

            @Override
            public String generateMessageNum() {
                return "MSG-" + seq.incrementAndGet();
            }

            @Override
            public String generateInvocationTraceNum() {
                return "TRC-X";
            }
        });
        session.setMessageRepository(new MessageRepository() {
            @Override
            public void save(Message entity) {
                saved.add(entity);
            }

            @Override
            public Message findByNum(String num) {
                return null;
            }

            @Override
            public void deleteByNum(String num) {
            }
        });

        session.appendUserMessage("你好", InputType.TEXT, "SES-1", "RUN-1", "alice");
        TurnTokenUsage usage = TurnTokenUsage.builder()
                .inputTokens(10)
                .outputTokens(4)
                .cachedTokens(1)
                .totalTokens(14)
                .build();
        session.appendAssistantMessage("好的", null, null, null, "SES-1", "RUN-1", usage, "alice");

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getNum()).isEqualTo("MSG-1");
        assertThat(saved.get(0).getRunId()).isEqualTo("RUN-1");
        assertThat(saved.get(0).getTurnTokenUsage()).isNull();
        assertThat(saved.get(1).getNum()).isEqualTo("MSG-2");
        assertThat(saved.get(1).getRunId()).isEqualTo("RUN-1");
        assertThat(saved.get(1).getTurnTokenUsage().getInputTokens()).isEqualTo(10);
        assertThat(saved.get(1).getTurnTokenUsage().getOutputTokens()).isEqualTo(4);
        assertThat(saved.get(1).getTurnTokenUsage().getCachedTokens()).isEqualTo(1);
        assertThat(saved.get(1).getTurnTokenUsage().getTotalTokens()).isEqualTo(14);
    }
}
