package ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox;

import java.util.Map;

/**
 * OpenSandbox 会话执行桥：把 Harness 沙箱的 exec / 写文件能力接到平台既有会话复用链路。
 * <p>
 * <b>分层约束</b>：本接口落在 infra，避免 Harness SPI 依赖 application 的 {@code SandboxRunner}。
 * 应用层在装配 {@link OpenSandboxFilesystemSpec} 时注入实现，典型写法为
 * {@code obtainSession} + {@code runInSession} / {@code files().writeFile}。
 */
public interface OpenSandboxExecBridge {

    /**
     * 在指定容器的会话级 bash 中执行一条命令。
     *
     * @param instanceId OpenSandbox 容器实例 id
     * @param sessionNum 平台会话编号（bash session 复用键）
     * @param env        会话环境变量（可空）
     * @param ttlMinutes session 映射滑动 TTL（分钟）
     * @param command    完整 shell 命令
     * @return 退出码与 stdout/stderr
     * @throws Exception 连接 / 执行失败时上抛
     */
    OpenSandboxCommandResult exec(
            String instanceId,
            String sessionNum,
            Map<String, String> env,
            long ttlMinutes,
            String command)
            throws Exception;

    /**
     * 向容器写入 UTF-8 文本文件（不存在则创建，存在则覆盖）。
     * <p>
     * workspace 投影 hydrate（tar+base64）依赖本方法落盘中间文件。
     *
     * @param instanceId   OpenSandbox 容器实例 id
     * @param sessionNum   平台会话编号
     * @param env          会话环境变量（可空）
     * @param ttlMinutes   session 映射滑动 TTL（分钟）
     * @param absolutePath 容器内绝对路径
     * @param utf8Content  文本内容
     * @throws Exception 写入失败时上抛
     */
    void writeTextFile(
            String instanceId,
            String sessionNum,
            Map<String, String> env,
            long ttlMinutes,
            String absolutePath,
            String utf8Content)
            throws Exception;
}
