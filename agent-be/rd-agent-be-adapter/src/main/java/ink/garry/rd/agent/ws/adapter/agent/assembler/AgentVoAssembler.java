package ink.garry.rd.agent.ws.adapter.agent.assembler;

import ink.garry.rd.agent.ws.client.agent.A2aSourceVO;
import ink.garry.rd.agent.ws.client.agent.A2aSyncHistoryVO;
import ink.garry.rd.agent.ws.client.agent.AgentDetailVO;
import ink.garry.rd.agent.ws.client.agent.AgentVO;
import ink.garry.rd.agent.ws.client.agent.AgentVersionDetailVO;
import ink.garry.rd.agent.ws.client.agent.AgentVersionVO;
import ink.garry.rd.agent.ws.client.agent.dto.A2aSourceViewDTO;
import ink.garry.rd.agent.ws.client.agent.dto.AgentA2aSyncHistoryDTO;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDetailViewDTO;
import ink.garry.rd.agent.ws.client.agent.dto.AgentListItemDTO;
import ink.garry.rd.agent.ws.client.agent.dto.AgentVersionDetailViewDTO;
import ink.garry.rd.agent.ws.client.agent.dto.AgentVersionViewDTO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 查询出参装配器（adapter 层）：把 application 层返回的 DTO 转为对外 VO。
 * <p>
 * 与 {@code SkillVoAssembler} 同范式：application 只返 client.agent.dto 下的 DTO，
 * adapter 经本装配器转为 client.agent 下的 VO 作为接口出参，隔离读模型与出参契约。
 * 字段一一对应，纯字段拷贝，无业务逻辑。
 */
@Component
public class AgentVoAssembler {

    /** 列表分页 DTO → VO。 */
    public PageVO<AgentVO> toAgentPageVo(PageVO<AgentListItemDTO> page) {
        if (page == null) {
            return null;
        }
        List<AgentVO> list = page.getList() == null ? List.of()
                : page.getList().stream().map(this::toAgentVo).toList();
        return PageVO.of(list, page.getTotal(), page.getPageNo(), page.getPageSize());
    }

    /** 列表项 DTO → VO。 */
    public AgentVO toAgentVo(AgentListItemDTO d) {
        if (d == null) {
            return null;
        }
        AgentVO vo = new AgentVO();
        vo.setNum(d.getNum());
        vo.setName(d.getName());
        vo.setDescription(d.getDescription());
        vo.setStatus(d.getStatus());
        vo.setSkillNum(d.getSkillNum());
        vo.setSkillNames(d.getSkillNames());
        vo.setAgentSource(d.getAgentSource());
        vo.setCreationMode(d.getCreationMode());
        vo.setCreateTime(d.getCreateTime());
        vo.setUpdateTime(d.getUpdateTime());
        return vo;
    }

    /** 详情 DTO → VO。 */
    public AgentDetailVO toAgentDetailVo(AgentDetailViewDTO d) {
        if (d == null) {
            return null;
        }
        AgentDetailVO vo = new AgentDetailVO();
        vo.setNum(d.getNum());
        vo.setName(d.getName());
        vo.setDescription(d.getDescription());
        vo.setTags(d.getTags());
        vo.setCreationMode(d.getCreationMode());
        vo.setAgentType(d.getAgentType());
        vo.setOwnerUserId(d.getOwnerUserId());
        vo.setStatus(d.getStatus());
        vo.setCurrentVersionNum(d.getCurrentVersionNum());
        vo.setCurrentSnapshot(d.getCurrentSnapshot());
        vo.setCurrentVersion(toAgentVersionDetailVo(d.getCurrentVersion()));
        vo.setHasDraft(d.getHasDraft());
        vo.setDraftEditor(d.getDraftEditor());
        vo.setDraftLockUntil(d.getDraftLockUntil());
        vo.setA2aSource(toA2aSourceVo(d.getA2aSource()));
        vo.setCreateTime(d.getCreateTime());
        vo.setUpdateTime(d.getUpdateTime());
        return vo;
    }

    /** 版本列表 DTO → VO。 */
    public List<AgentVersionVO> toAgentVersionVoList(List<AgentVersionViewDTO> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream().map(this::toAgentVersionVo).toList();
    }

    /** 版本 DTO → VO。 */
    public AgentVersionVO toAgentVersionVo(AgentVersionViewDTO d) {
        if (d == null) {
            return null;
        }
        AgentVersionVO vo = new AgentVersionVO();
        fillVersionVo(vo, d);
        return vo;
    }

    /** 版本详情 DTO → VO（含 snapshot 双字段）。 */
    public AgentVersionDetailVO toAgentVersionDetailVo(AgentVersionDetailViewDTO d) {
        if (d == null) {
            return null;
        }
        AgentVersionDetailVO vo = new AgentVersionDetailVO();
        fillVersionVo(vo, d);
        vo.setSnapshot(d.getSnapshot());
        vo.setConfigSnapshot(d.getConfigSnapshot());
        return vo;
    }

    /** A2A 同步历史 DTO → VO。 */
    public List<A2aSyncHistoryVO> toA2aSyncHistoryVoList(List<AgentA2aSyncHistoryDTO> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream().map(this::toA2aSyncHistoryVo).toList();
    }

    /** A2A 同步历史单条 DTO → VO。 */
    public A2aSyncHistoryVO toA2aSyncHistoryVo(AgentA2aSyncHistoryDTO d) {
        if (d == null) {
            return null;
        }
        A2aSyncHistoryVO vo = new A2aSyncHistoryVO();
        vo.setId(d.getId());
        vo.setRemoteVersion(d.getRemoteVersion());
        vo.setSyncEventType(d.getSyncEventType());
        vo.setTriggeredBy(d.getTriggeredBy());
        vo.setSyncedAt(d.getSyncedAt());
        vo.setAgentCardJson(d.getAgentCardJson());
        return vo;
    }

    // ---- private helpers ----

    /** 版本 VO 公共字段填充（VersionVO 与 DetailVO 共用）。 */
    private void fillVersionVo(AgentVersionVO vo, AgentVersionViewDTO d) {
        vo.setNum(d.getNum());
        vo.setAgentNum(d.getAgentNum());
        vo.setStatus(d.getStatus());
        vo.setVersionNum(d.getVersionNum());
        vo.setRemark(d.getRemark());
        vo.setPublishedBy(d.getPublishedBy());
        vo.setPublishedAt(d.getPublishedAt());
        vo.setCurrent(d.getCurrent());
        vo.setEditorUserId(d.getEditorUserId());
        vo.setLockUntil(d.getLockUntil());
        vo.setConfigSnapshot(d.getConfigSnapshot());
    }

    /** A2A 来源 DTO → VO（含 remoteSkills / remoteMcps 嵌套）。 */
    private A2aSourceVO toA2aSourceVo(A2aSourceViewDTO d) {
        if (d == null) {
            return null;
        }
        A2aSourceVO vo = new A2aSourceVO();
        vo.setNacosGroup(d.getNacosGroup());
        vo.setNacosService(d.getNacosService());
        vo.setInstanceIp(d.getInstanceIp());
        vo.setInstancePort(d.getInstancePort());
        vo.setEndpointPath(d.getEndpointPath());
        vo.setRemoteVersion(d.getRemoteVersion());
        vo.setAgentCardJson(d.getAgentCardJson());
        vo.setLastSyncedAt(d.getLastSyncedAt());
        vo.setLastSyncEventType(d.getLastSyncEventType());
        if (d.getRemoteSkills() != null) {
            vo.setRemoteSkills(d.getRemoteSkills().stream().map(rs -> {
                A2aSourceVO.RemoteSkill out = new A2aSourceVO.RemoteSkill();
                out.setName(rs.getName());
                out.setDescription(rs.getDescription());
                return out;
            }).toList());
        }
        if (d.getRemoteMcps() != null) {
            vo.setRemoteMcps(d.getRemoteMcps().stream().map(rm -> {
                A2aSourceVO.RemoteMcp out = new A2aSourceVO.RemoteMcp();
                out.setName(rm.getName());
                out.setDescription(rm.getDescription());
                out.setServerUrl(rm.getServerUrl());
                return out;
            }).toList());
        }
        return vo;
    }
}
