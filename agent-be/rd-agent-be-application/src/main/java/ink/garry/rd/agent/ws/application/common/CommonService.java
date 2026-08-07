package ink.garry.rd.agent.ws.application.common;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.common.employee.EmployeeProfileDTO;
import ink.garry.rd.agent.ws.client.common.employee.EmployeeSearchParamDTO;
import ink.garry.rd.agent.ws.client.workspace.constant.WorkspaceConstants;
import ink.garry.rd.agent.ws.infra.common.client.cloudbus.CloudBusClient;
import ink.garry.rd.agent.ws.infra.common.client.cloudbus.dto.EmployeeDTO;
import ink.garry.rd.agent.ws.infra.common.client.oss.OssClient;
import ink.garry.rd.agent.ws.infra.common.client.oss.dto.OssPresignResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Common 横切服务：把 infra 层 {@link OssClient} 的方法适配为面向前端的用例。
 * <p>
 * <b>职责</b>：
 * <ul>
 *   <li>把前端 {@code client.common.oss.*Param} 翻译成 infra {@code oss.param.*}，调 {@link OssClient}；</li>
 *   <li>不做权限校验 / 审计（横切 OSS 操作目前无需归属人）— 如未来需要按 user 限桶，在此扩展。</li>
 * </ul>
 * <p>
 * <b>分层约束</b>：本服务**不**走 {@code @Transactional}（OSS 操作不参与 PG 事务）；
 * 不依赖任何 domain 聚合；签名层只暴露 client 类型，避免 infra 类型泄漏到 adapter
 * （见 ARCHITECTURE §3.1）。
 */
@Service
@RequiredArgsConstructor
public class CommonService {

    private final OssClient ossClient;
    private final CloudBusClient cloudBusClient;

    /**
     * 请求 oss 签名凭证。
     *
     * @param fileName 文件名
     * @return 签名凭证 VO
     */
    public OssPresignResultDTO requestStsCredential(String fileName) {
        return ossClient.uploadPresign(fileName);
    }

    /**
     * 获取文件访问 URL。
     * @param fileId 文件 ID
     * @return 文件访问 URL
     */
    public String getFileUrl(String fileId) {
        return ossClient.generateDownloadUrl(fileId);
    }

    /**
     * 通用员工搜索（工号 / 姓名）。
     * <p>跨领域通用能力，挂在 Common 服务，任何模块可复用（如工作空间编辑时选成员）。
     * CloudBus 已下线时返回空列表。
     *
     * @param param 搜索入参（keyword ≥ 2 字符；limit 默认 20，最大 50）
     * @return 员工档案列表；无命中返回空列表
     */
    public List<EmployeeProfileDTO> searchEmployees(EmployeeSearchParamDTO param) {
        if (param == null || StrUtil.isBlank(param.getKeyword())) {
            return new ArrayList<>();
        }

        int limit = (param.getLimit() == null || param.getLimit() < 1)
                ? WorkspaceConstants.SEARCH_LIMIT_DEFAULT
                : Math.min(param.getLimit(), WorkspaceConstants.SEARCH_LIMIT_MAX);

        List<EmployeeDTO> employeeDTOList = cloudBusClient.searchEmployee(param.getKeyword(), limit);

        if (CollectionUtil.isEmpty(employeeDTOList)) {
            return new ArrayList<>();
        }

        List<EmployeeProfileDTO> result = new ArrayList<>();
        for (EmployeeDTO employeeDTO : employeeDTOList) {
            result.add(EmployeeProfileDTO.builder()
                    .empNo(employeeDTO.getAdName())
                    .displayName(employeeDTO.getRealName())
                    .build());
        }

        return result;
    }
}
