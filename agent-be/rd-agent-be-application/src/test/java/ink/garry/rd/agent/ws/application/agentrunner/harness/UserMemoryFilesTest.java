package ink.garry.rd.agent.ws.application.agentrunner.harness;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserMemoryFilesTest {

    @Test
    void mergeDaily_keepsOtherDaysAndSkipsBlank() {
        LocalDate day = LocalDate.of(2026, 9, 19);
        String merged = UserMemoryFiles.mergeDaily("{\"2026-09-18\":\"old\"}", day, "today");
        Map<String, String> map = UserMemoryFiles.parseDaily(merged);
        assertEquals("old", map.get("2026-09-18"));
        assertEquals("today", map.get("2026-09-19"));

        String unchanged = UserMemoryFiles.mergeDaily(merged, day, "  ");
        assertEquals("today", UserMemoryFiles.parseDaily(unchanged).get("2026-09-19"));
    }

    @Test
    void parseDaily_badJsonIsEmpty() {
        assertTrue(UserMemoryFiles.parseDaily("not-json").isEmpty());
        assertEquals("memory/2026-09-19.md", UserMemoryFiles.dailyRelative(LocalDate.of(2026, 9, 19)));
    }
}
