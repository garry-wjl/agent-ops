package ink.garry.rd.agent.ws.domain.sandbox.valueobject;

/**
 * 沙箱类型枚举。
 * <p>
 * S2 本期仅支持 {@link #CODE 代码沙箱} 一种类型；枚举预留扩展位，
 * 后续新增类型（如数据沙箱、浏览器沙箱等）时在此追加，避免改表结构。
 */
public enum SandboxType {

    /** 代码沙箱：用于代码生成 / 执行 / 命令运行类任务的隔离执行环境。 */
    CODE
}
