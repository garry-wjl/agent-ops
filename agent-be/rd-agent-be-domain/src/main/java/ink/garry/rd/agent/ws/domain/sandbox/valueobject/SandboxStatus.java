package ink.garry.rd.agent.ws.domain.sandbox.valueobject;

/**
 * 沙箱生命周期状态枚举。
 * <p>
 * 状态流转（详见沙箱管理技术方案 §4.2.2）：
 * <ul>
 *   <li>新建 → {@link #DRAFT}（首次 save 落库）</li>
 *   <li>{@link #DRAFT} / {@link #FAILED} → {@link #INITIALIZED}（执行 submit 提交后；不在此调 OpenSandbox）</li>
 *   <li>{@link #INITIALIZED} → {@link #ONLINE}（异步供给：容器创建 + 健康检查通过后 online）</li>
 *   <li>{@link #INITIALIZED} → {@link #FAILED}（异步供给失败：markProvisionFailed）</li>
 *   <li>{@link #ONLINE} → {@link #OFFLINE}（执行 offline 下线，联动 kill 容器）</li>
 *   <li>{@link #OFFLINE} → {@link #INITIALIZED}（执行 reonline 重新上线，重走供给流程）</li>
 * </ul>
 */
public enum SandboxStatus {

    /** 草稿态：仅平台侧元数据，尚未在 OpenSandbox 创建任何容器实例；可改全部字段、可删除。 */
    DRAFT,

    /** 初始化态：已提交，等待异步供给（创建容器 + 健康检查）；规格锁定，仅备注可改。 */
    INITIALIZED,

    /** 在线态：容器已就绪、可被使用；仅备注可改，不可删除（需先下线）。 */
    ONLINE,

    /** 下线态：容器已停止 / 释放；可重新上线或删除。 */
    OFFLINE,

    /** 失败态：初始化（供给）失败；可重新提交、编辑规格后重试或删除。 */
    FAILED
}
