package ink.garry.rd.agent.ws.application.agentrunner.harness;

/**
 * 把模型传入的工作目录收成沙箱 workspace 内的相对路径。
 * Harness {@code execute} 会拒绝以 {@code /} 或 {@code ~} 开头、或含 {@code ..} 的 working_directory，
 * 但技能提示词给出的 files-root 是绝对路径（ACS 为 {@code /home/user}）。
 */
public final class WorkspaceRelativePaths {

    public static final String REJECTED =
            "Error: working_directory must be a relative path within the workspace"
                    + " (absolute paths, '~', and '..' are not allowed).";

    private static final String[] FALLBACK_ROOTS = {"/home/user", "/workspace"};

    private WorkspaceRelativePaths() {
    }

    /**
     * @param workingDirectory 模型传入的工作目录，可空
     * @param primaryRoot      当前沙箱 workspace 根
     * @return {@code null} 表示不必额外 cd；非 null 且不是 {@link #REJECTED} 时为相对路径
     */
    public static String toRelativeOrError(String workingDirectory, String primaryRoot) {
        if (workingDirectory == null || workingDirectory.isBlank()) {
            return null;
        }
        String wd = workingDirectory.strip();
        if (wd.startsWith("~") || wd.contains("..")) {
            return REJECTED;
        }
        if (!wd.startsWith("/")) {
            return wd;
        }
        String relative = stripRoot(wd, primaryRoot);
        if (relative == null) {
            for (String root : FALLBACK_ROOTS) {
                relative = stripRoot(wd, root);
                if (relative != null) {
                    break;
                }
            }
        }
        if (relative == null) {
            return REJECTED;
        }
        if (relative.isEmpty() || ".".equals(relative)) {
            return null;
        }
        return relative;
    }

    private static String stripRoot(String path, String root) {
        if (root == null || root.isBlank() || "/".equals(root.trim())) {
            return null;
        }
        String normalized = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        if (path.equals(normalized)) {
            return "";
        }
        String prefix = normalized + "/";
        if (path.startsWith(prefix)) {
            String rest = path.substring(prefix.length());
            while (rest.endsWith("/")) {
                rest = rest.substring(0, rest.length() - 1);
            }
            return rest;
        }
        return null;
    }
}
