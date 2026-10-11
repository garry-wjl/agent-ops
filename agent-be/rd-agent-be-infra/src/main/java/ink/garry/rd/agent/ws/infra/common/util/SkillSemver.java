package ink.garry.rd.agent.ws.infra.common.util;

import cn.hutool.core.util.StrUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 版本号（Semver x.y.z）工具：下一 patch 递增。
 * <p>
 * 与 {@link SkillChecker} 的 Semver 校验对齐；升级压缩包场景默认只 bump patch。
 */
public final class SkillSemver {

    private static final Pattern CORE = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)");

    private SkillSemver() {
    }

    /**
     * 基于当前版本生成下一 patch；当前为空或无法解析时从 {@code 1.0.0} 起步。
     * <p>
     * 例：{@code null → 1.0.0}，{@code 1.2.3 → 1.2.4}，{@code 1.0.0-beta → 1.0.1}（丢弃预发布后缀）。
     *
     * @param currentVersion 当前在线版本号（可空）
     * @return 下一版本号
     */
    public static String nextPatch(String currentVersion) {
        if (StrUtil.isBlank(currentVersion)) {
            return "1.0.0";
        }
        Matcher m = CORE.matcher(currentVersion.trim());
        if (!m.find()) {
            return "1.0.0";
        }
        int major = Integer.parseInt(m.group(1));
        int minor = Integer.parseInt(m.group(2));
        int patch = Integer.parseInt(m.group(3));
        return major + "." + minor + "." + (patch + 1);
    }
}
