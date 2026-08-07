package ink.garry.rd.agent.ws.domain.agent.valueobject;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Semver 版本号值对象（major.minor.patch）。
 * <p>
 * 单调递增，回滚不复用旧号；与 AgentVersion.versionNum 字符串保持双向同步。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Version {

    /** 主版本号；破坏性变更递增 */
    private int major;
    /** 次版本号；功能性变更递增 */
    private int minor;
    /** 修订号；微调递增 */
    private int patch;

    /**
     * 首版本，固定 v1.0.0；用于 Agent 首次发布。
     *
     * @return new Version(1,0,0)
     */
    public static Version initial() {
        return new Version(1, 0, 0);
    }

    /**
     * 解析版本号字符串（兼容前导 v）。
     *
     * @param versionNum 形如 v1.2.3 或 1.2.3
     * @return 对应 Version 实例
     * @throws IllegalArgumentException 格式不合法时抛出
     */
    public static Version parse(String versionNum) {
        Assert.notBlank(versionNum, "版本号不能为空");
        String trimmed = versionNum.startsWith("v") ? versionNum.substring(1) : versionNum;
        String[] parts = trimmed.split("\\.");
        Assert.isTrue(parts.length == 3, "版本号格式必须为 vX.Y.Z：{}", versionNum);
        return new Version(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        );
    }

    /**
     * 推进到下一个版本号：固定 patch+1（单调递增，不复用旧号）。
     * <p>
     * v3.1（Agent 优化）：移除 {@code ChangeLevel} 入参，发布不再要求用户选择变更等级，
     * 统一按 patch 递进；首版本由 {@link #initial()} 提供。
     *
     * @return 新版本号实例（不修改当前实例）
     */
    public Version next() {
        return new Version(major, minor, patch + 1);
    }

    /**
     * 转字符串形式 vX.Y.Z，用于持久化到 versionNum 字段。
     */
    public String toStr() {
        return StrUtil.format("v{}.{}.{}", major, minor, patch);
    }
}
