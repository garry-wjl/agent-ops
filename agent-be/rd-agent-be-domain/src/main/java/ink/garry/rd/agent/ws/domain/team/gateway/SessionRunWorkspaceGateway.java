package ink.garry.rd.agent.ws.domain.team.gateway;

import java.util.Optional;

/**
 * 会话与本次编排运行共享目录的绑定。
 * <p>
 * 沙箱创建时按会话号取出 subPath，把同一块 OSS 前缀挂进该 Agent 的容器。
 */
public interface SessionRunWorkspaceGateway {

    /**
     * 记下这个会话要挂的运行目录。重复绑定覆盖为同一次运行的路径。
     *
     * @param sessionNum 会话编号
     * @param runNum     运行编号
     * @param subPath    {@link ink.garry.rd.agent.ws.domain.team.RunWorkspaceMount#subPath}
     */
    void bind(String sessionNum, String runNum, String subPath);

    /**
     * @param sessionNum 会话编号
     * @return 运行目录 subPath；普通调试会话为空
     */
    Optional<String> findSubPath(String sessionNum);
}
