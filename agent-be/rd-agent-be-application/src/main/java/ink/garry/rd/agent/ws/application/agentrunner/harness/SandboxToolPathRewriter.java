package ink.garry.rd.agent.ws.application.agentrunner.harness;

import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.sandbox.AbstractSandboxFilesystem;
import io.agentscope.harness.agent.tool.FilesystemTool;
import io.agentscope.harness.agent.workspace.WorkspacePathNormalizer;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;

/**
 * Harness 把沙箱文件工具的路径前缀写死成 {@code /workspace}，且 {@code execute} 拒绝绝对 working_directory。
 * 构建完成后换成当前 workspace 根（ACS 为 {@code /home/user}），并同时认 {@code /workspace}。
 */
public final class SandboxToolPathRewriter {

    private static final String[] FILE_TOOLS = {
            "read_file", "write_file", "edit_file", "grep_files", "glob_files", "list_files"
    };

    private SandboxToolPathRewriter() {
    }

    public static void rewrite(Toolkit toolkit, String workspaceRoot) {
        if (toolkit == null) {
            return;
        }
        String root = workspaceRoot != null && !workspaceRoot.isBlank() ? workspaceRoot : "/workspace";
        replaceShell(toolkit, root);
        replaceFilesystem(toolkit, root);
    }

    private static void replaceShell(Toolkit toolkit, String root) {
        AbstractSandboxFilesystem sandbox = find(toolkit.getTool("execute"), AbstractSandboxFilesystem.class, 0, new IdentityHashMap<>());
        if (sandbox == null) {
            return;
        }
        toolkit.removeTool("execute");
        toolkit.registerTool(new WorkspaceShellTool(sandbox, root));
    }

    private static void replaceFilesystem(Toolkit toolkit, String root) {
        AbstractFilesystem filesystem = find(toolkit.getTool("read_file"), AbstractFilesystem.class, 0, new IdentityHashMap<>());
        if (filesystem == null) {
            return;
        }
        for (String name : FILE_TOOLS) {
            toolkit.removeTool(name);
        }
        toolkit.registerTool(new FilesystemTool(
                filesystem,
                WorkspacePathNormalizer.of(root, "/home/user", "/workspace")));
    }

    private static <T> T find(Object current, Class<T> type, int depth, IdentityHashMap<Object, Boolean> seen) {
        if (current == null || depth > 6 || seen.containsKey(current)) {
            return null;
        }
        if (type.isInstance(current)) {
            return type.cast(current);
        }
        if (current instanceof AgentTool || current.getClass().getName().startsWith("io.agentscope.")) {
            seen.put(current, Boolean.TRUE);
            Class<?> cursor = current.getClass();
            while (cursor != null && cursor != Object.class) {
                for (Field field : cursor.getDeclaredFields()) {
                    if (field.getType().isPrimitive()) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        T found = find(field.get(current), type, depth + 1, seen);
                        if (found != null) {
                            return found;
                        }
                    } catch (IllegalAccessException ignored) {
                        // 跳过不可读字段
                    }
                }
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }
}
