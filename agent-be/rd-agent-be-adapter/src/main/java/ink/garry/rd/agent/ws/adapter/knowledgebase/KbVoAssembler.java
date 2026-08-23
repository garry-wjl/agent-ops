package ink.garry.rd.agent.ws.adapter.knowledgebase;

import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbChunkDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbCreateParamDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbDetailDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbFileDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbListParamDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbTestRetrieveParamDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbTypeSchemaDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbUpdateBasicParamDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbUpdateIndexConfigParamDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbUploadFileParamDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.MountableKbItemDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbChunkVo;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbCreateParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbDetailVo;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbFileVo;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbListQueryParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbTestRetrieveParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbTypeSchemaVo;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbUpdateBasicParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbUpdateIndexConfigParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbUploadFileParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbVo;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.MountableKbItemVo;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class KbVoAssembler {

    public KbCreateParamDTO toCreateDTO(KbCreateParam param) {
        KbCreateParamDTO dto = new KbCreateParamDTO();
        BeanUtils.copyProperties(param, dto);
        return dto;
    }

    public KbUpdateBasicParamDTO toUpdateBasicDTO(KbUpdateBasicParam param) {
        KbUpdateBasicParamDTO dto = new KbUpdateBasicParamDTO();
        BeanUtils.copyProperties(param, dto);
        return dto;
    }

    public KbUpdateIndexConfigParamDTO toUpdateIndexConfigDTO(KbUpdateIndexConfigParam param) {
        KbUpdateIndexConfigParamDTO dto = new KbUpdateIndexConfigParamDTO();
        BeanUtils.copyProperties(param, dto);
        return dto;
    }

    public KbUploadFileParamDTO toUploadDTO(KbUploadFileParam param) {
        KbUploadFileParamDTO dto = new KbUploadFileParamDTO();
        BeanUtils.copyProperties(param, dto);
        return dto;
    }

    public KbTestRetrieveParamDTO toTestRetrieveDTO(KbTestRetrieveParam param) {
        KbTestRetrieveParamDTO dto = new KbTestRetrieveParamDTO();
        BeanUtils.copyProperties(param, dto);
        return dto;
    }

    public KbListParamDTO toListDTO(KbListQueryParam param) {
        KbListParamDTO dto = new KbListParamDTO();
        if (param != null) {
            BeanUtils.copyProperties(param, dto);
        }
        return dto;
    }

    public KbVo toKbVo(KbDTO dto) {
        KbVo vo = new KbVo();
        BeanUtils.copyProperties(dto, vo);
        return vo;
    }

    public PageVO<KbVo> toKbPageVO(PageVO<KbDTO> page) {
        List<KbVo> list = page.getList().stream().map(this::toKbVo).collect(Collectors.toList());
        return PageVO.of(list, page.getTotal(), page.getPageNo(), page.getPageSize());
    }

    public KbDetailVo toDetailVo(KbDetailDTO dto) {
        KbDetailVo vo = new KbDetailVo();
        BeanUtils.copyProperties(dto, vo);
        return vo;
    }

    public List<KbFileVo> toFileVoList(List<KbFileDTO> list) {
        return list.stream().map(d -> {
            KbFileVo vo = new KbFileVo();
            BeanUtils.copyProperties(d, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    public List<MountableKbItemVo> toMountableVoList(List<MountableKbItemDTO> list) {
        return list.stream().map(d -> {
            MountableKbItemVo vo = new MountableKbItemVo();
            BeanUtils.copyProperties(d, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    public List<KbTypeSchemaVo> toTypeSchemaVoList(List<KbTypeSchemaDTO> list) {
        return list.stream().map(d -> {
            KbTypeSchemaVo vo = new KbTypeSchemaVo();
            BeanUtils.copyProperties(d, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    public List<KbChunkVo> toChunkVoList(List<KbChunkDTO> list) {
        return list.stream().map(d -> {
            KbChunkVo vo = new KbChunkVo();
            BeanUtils.copyProperties(d, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}
