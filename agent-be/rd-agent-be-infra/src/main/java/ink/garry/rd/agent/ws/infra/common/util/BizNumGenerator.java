package ink.garry.rd.agent.ws.infra.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 业务编号生成器（按总体方案 §10.3）。
 * <p>
 * 格式：{TYPE}{yyyyMMddHHmm}{4 位序号}，例如 AGT2026051114301234。
 * 同一分钟同类资源最多 9999 条；以 Snowflake.nextId() % 10000 取尾 4 位作为序号。
 */
@Component
public class BizNumGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final Snowflake snowflake;

    public BizNumGenerator(@Value("${app.snowflake.worker-id:1}") long workerId) {
        this.snowflake = new Snowflake(workerId);
    }

    public String generate(String typePrefix) {
        String time = LocalDateTime.now().format(FMT);
        long seq = snowflake.nextId() % 10000;
        return String.format("%s%s%04d", typePrefix, time, seq);
    }

    public long nextLongId() {
        return snowflake.nextId();
    }
}
