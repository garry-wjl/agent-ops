package ink.garry.rd.agent.ws.domain.sandbox.valueobject;

/**
 * 会话工作空间在 OSS 上的隔离前缀。
 * <p>
 * 挂到容器 {@code /workspace} 的子路径是 {@code {workspaceNum}/{agentNum}/{sessionNum}}。
 * 不按容器实例划分：容器可丢弃，会话前缀才是隔离键。
 */
public final class SessionWorkspaceMount {

    /** 容器内挂载点，与 Harness 沙箱工作空间根一致 */
    public static final String MOUNT_PATH = "/workspace";

    private SessionWorkspaceMount() {
    }

    /**
     * @param workspaceNum 工作空间编号
     * @param agentNum     Agent 编号
     * @param sessionNum   会话编号
     * @return OSS subPath，不含首尾斜杠
     */
    public static String subPath(String workspaceNum, String agentNum, String sessionNum) {
        return segment(workspaceNum, "workspaceNum")
                + "/" + segment(agentNum, "agentNum")
                + "/" + segment(sessionNum, "sessionNum");
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
