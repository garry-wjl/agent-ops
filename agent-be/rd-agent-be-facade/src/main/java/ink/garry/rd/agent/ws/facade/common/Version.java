package ink.garry.rd.agent.ws.facade.common;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 版本值对象：遵循语义化版本（Semantic Versioning）vX.Y.Z 格式。
 * 字符串形态形如 {@code v1.0.0}，支持解析、初始化与按 {@link ChangeLevel} 递进。
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Version {
    /** 主版本号：不兼容变更时递增 */
    private int major;
    /** 次版本号：向下兼容的功能新增时递增 */
    private int minor;
    /** 补丁号：向下兼容的缺陷修复时递增 */
    private int patch;

    /**
     * 返回初始版本 v1.0.0。
     *
     * @return 初始版本对象
     */
    public static Version initial() {
        return new Version(1, 0, 0);
    }

    /**
     * 解析版本号字符串。
     * 支持带前缀 {@code v} 或不带前缀；非 vX.Y.Z 格式将抛出断言异常。
     *
     * @param versionNum 版本字符串，如 "v1.2.3" 或 "1.2.3"
     * @return 解析后的版本对象
     */
    public static Version parse(String versionNum) {
        Assert.notBlank(versionNum, "版本号不能为空");
        String trimmed = versionNum.startsWith("v") ? versionNum.substring(1) : versionNum;
        String[] parts = trimmed.split("\\.");
        Assert.isTrue(parts.length == 3, "版本号格式必须为 vX.Y.Z：{}", versionNum);
        return new Version(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    /**
     * 按变更级别递进版本号。
     * PATCH：patch +1；MINOR：minor +1、patch 归零；MAJOR：major +1、minor / patch 归零。
     *
     * @param level 变更级别
     * @return 递进后的新版本对象（不修改当前对象）
     */
    public Version next(ChangeLevel level) {
        return switch (level) {
            case PATCH -> new Version(major, minor, patch + 1);
            case MINOR -> new Version(major, minor + 1, 0);
            case MAJOR -> new Version(major + 1, 0, 0);
        };
    }

    /**
     * 转为 vX.Y.Z 字符串表示。
     *
     * @return 形如 "v1.0.0" 的版本字符串
     */
    public String toStr() {
        return StrUtil.format("v{}.{}.{}", major, minor, patch);
    }
}
