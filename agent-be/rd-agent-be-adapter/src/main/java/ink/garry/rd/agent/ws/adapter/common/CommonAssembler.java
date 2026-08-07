package ink.garry.rd.agent.ws.adapter.common;

import ink.garry.rd.agent.ws.application.common.CommonService;
import ink.garry.rd.agent.ws.client.common.employee.EmployeeProfileDTO;
import ink.garry.rd.agent.ws.client.common.employee.EmployeeProfileVO;
import ink.garry.rd.agent.ws.client.common.oss.OssPresignResultVO;
import ink.garry.rd.agent.ws.infra.common.client.oss.dto.OssPresignResultDTO;

/**
 * Common 模块 application 层装配器:infra OSS DTO → client VO 字段映射。
 * <p>
 * 装配器放在 application 层而非 adapter 层,因为 ARCHITECTURE §3.1 禁止
 * {@code adapter → infra} 业务类型依赖;adapter 不能感知 infra 的
 * {@link OssPresignResultDTO} 等类型。
 * <p>
 * <b>使用约束</b>:
 * <ul>
 *   <li>仅由 {@link CommonService} 调用;adapter / domain 严禁依赖;</li>
 *   <li>纯静态方法、无副作用;入参为 null 时返回 null;</li>
 *   <li>新增 common 横切 DTO/VO 转换时在此追加 {@code toXxxVO}。</li>
 * </ul>
 */
public final class CommonAssembler {

    /** 工具类禁止实例化。 */
    private CommonAssembler() {}

    /**
     * infra {@link OssPresignResultDTO} → client {@link OssPresignResultVO}。
     *
     * @param dto infra 出参，可空
     * @return 前端 VO；入参为 null 时返回 null
     */
    public static OssPresignResultVO toOssPresignResultVO(OssPresignResultDTO dto) {
        if (dto == null) {
            return null;
        }
        OssPresignResultVO vo = new OssPresignResultVO();
        vo.setFileId(dto.getFileId());
        vo.setUrl(dto.getUrl());
        vo.setMethod(dto.getMethod());
        vo.setExpiration(dto.getExpiration());
        vo.setSignedHeaders(dto.getSignedHeaders());
        return vo;
    }

    /**
     * client {@link EmployeeProfileDTO} → client {@link EmployeeProfileVO}（员工搜索结果）。
     *
     * @param dto application 出参，可空
     * @return 前端 VO；入参为 null 时返回 null
     */
    public static EmployeeProfileVO toEmployeeProfileVO(EmployeeProfileDTO dto) {
        if (dto == null) {
            return null;
        }
        EmployeeProfileVO vo = new EmployeeProfileVO();
        vo.setEmpNo(dto.getEmpNo());
        vo.setDisplayName(dto.getDisplayName());
        vo.setDept(dto.getDept());
        return vo;
    }




}
