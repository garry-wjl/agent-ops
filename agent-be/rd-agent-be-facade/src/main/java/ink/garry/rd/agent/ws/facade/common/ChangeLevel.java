package ink.garry.rd.agent.ws.facade.common;

/**
 * 变更级别枚举。
 * 描述一次版本发布所带来的兼容性影响，配合 {@link Version#next(ChangeLevel)} 进行版本号递进。
 */
public enum ChangeLevel {
    /** 补丁级：向下兼容的缺陷修复，patch +1 */
    PATCH,
    /** 次版本级：向下兼容的功能新增，minor +1，patch 归零 */
    MINOR,
    /** 主版本级：不兼容变更，major +1，minor / patch 归零 */
    MAJOR
}
