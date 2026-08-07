package ink.garry.rd.agent.ws.domain.session.factory;

import ink.garry.rd.agent.ws.domain.session.InvocationTrace;

/**
 * InvocationTrace 聚合工厂：构造已装配 Repository / NumGateway / Publisher 的实例。
 * <p>
 * 应用层不得自行 setter 注入 Repository / Publisher 等基础设施依赖。
 */
public interface InvocationTraceFactory {
    /**
     * 构造一个待保存的调用记录（未持久化）。num 由内部通过 SessionNumGateway 生成。
     *
     * @return 已分配 num 且装配完依赖的 InvocationTrace 实例
     */
    InvocationTrace create();
}
