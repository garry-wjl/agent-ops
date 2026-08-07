package ink.garry.rd.agent.ws.adapter.session;

import ink.garry.rd.agent.ws.client.session.SessionVO;
import ink.garry.rd.agent.ws.client.session.dto.SessionDTO;

/**
 * Session 模块 adapter 层公共装配器。
 * <p>
 * 职责：承接 application 层出参 DTO → Controller 出参 VO 的字段转换，
 * 集中维护 session 领域跨层数据形变规则；后续 session 相关的 adapter 转换方法都加入本类。
 * <p>
 * <b>使用约束</b>：
 * <ul>
 *   <li>仅在 adapter 层调用；application / domain / infra 严禁依赖；</li>
 *   <li>纯静态方法，无外部依赖、无副作用；空入参返回空出参，调用方按需判空；</li>
 *   <li>新增 session 相关 DTO/VO 转换时，统一在此追加 {@code toXxxVO} 静态方法。</li>
 * </ul>
 */
public final class SessionCommonAssembler {

    /** 工具类禁止实例化 */
    private SessionCommonAssembler() {}

    /**
     * SessionDTO → SessionVO 字段拷贝。
     * <p>
     * VO 字段是 DTO 的子集（不含 id / creatorUserId / createNo / updateNo / updateTime），
     * 仅暴露前端会话头部展示所需信息。
     *
     * @param dto application 层出参，可空
     * @return Controller 出参 VO；入参为 null 时返回 null
     */
    public static SessionVO toSessionVO(SessionDTO dto) {
        if (dto == null) {
            return null;
        }
        SessionVO vo = new SessionVO();
        vo.setNum(dto.getNum());
        vo.setAgentNum(dto.getAgentNum());
        vo.setAgentVersionNum(dto.getAgentVersionNum());
        vo.setSkillHint(dto.getSkillHint());
        vo.setTitle(dto.getTitle());
        vo.setLastMessageAt(dto.getLastMessageAt());
        vo.setCreateTime(dto.getCreateTime());
        vo.setOrigin(dto.getOrigin());
        return vo;
    }
}
