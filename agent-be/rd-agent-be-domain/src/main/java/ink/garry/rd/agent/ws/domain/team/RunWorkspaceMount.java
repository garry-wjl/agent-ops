package ink.garry.rd.agent.ws.domain.team;

/**
 * 一次编排运行在 OSS 上的共享工作目录。
 * <p>
 * 目录键是 {@code {workspaceNum}/runs/{runNum}}。这次运行里的每个 Agent 容器都把该前缀挂到工作目录根，
 * 因此后一个 Agent 能直接读到前一个 Agent 写下的文件。不同运行使用不同前缀。
 */
public final class RunWorkspaceMount {

    private RunWorkspaceMount() {
    }

    /**
     * @param workspaceNum 工作空间编号
     * @param runNum       本次运行编号
     * @return OSS / PVC subPath，不含首尾斜杠
     */
    public static String subPath(String workspaceNum, String runNum) {
        return segment(workspaceNum, "workspaceNum") + "/runs/" + segment(runNum, "runNum");
    }

    private static String segment(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        String value = raw.trim();
        if (value.contains("/") || value.contains("\\") || value.contains("..") || value.contains("\0")) {
            throw new IllegalArgumentException(name + " 含非法路径字符");
        }
        return value;
    }
}
