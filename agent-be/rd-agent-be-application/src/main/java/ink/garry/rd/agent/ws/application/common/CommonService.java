package ink.garry.rd.agent.ws.application.common;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.user.UserQueryService;
import ink.garry.rd.agent.ws.client.common.employee.EmployeeProfileDTO;
import ink.garry.rd.agent.ws.client.common.employee.EmployeeSearchParamDTO;
import ink.garry.rd.agent.ws.client.user.dto.UserBriefDTO;
import ink.garry.rd.agent.ws.client.workspace.constant.WorkspaceConstants;
import ink.garry.rd.agent.ws.infra.common.client.oss.OssClient;
import ink.garry.rd.agent.ws.infra.common.client.oss.dto.OssPresignResultDTO;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    @Resource
    private UserQueryService userQueryService;

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
     * 通用选人搜索：改为查启用态平台用户。
     * <p>{@code empNo} 字段承载 {@code User.num}；{@code displayName} 为 username。
     *
     * @param param 搜索入参（keyword；limit 默认 20，最大 50）
     * @return 用户简要列表；无命中返回空列表
     */
    public List<EmployeeProfileDTO> searchEmployees(EmployeeSearchParamDTO param) {
        if (param == null || StrUtil.isBlank(param.getKeyword())) {
            return new ArrayList<>();
        }

        int limit = (param.getLimit() == null || param.getLimit() < 1)
                ? WorkspaceConstants.SEARCH_LIMIT_DEFAULT
                : Math.min(param.getLimit(), WorkspaceConstants.SEARCH_LIMIT_MAX);

        List<UserBriefDTO> users = userQueryService.searchEnabledUsers(param.getKeyword().trim(), limit);
        List<EmployeeProfileDTO> result = new ArrayList<>();
        for (UserBriefDTO user : users) {
            result.add(EmployeeProfileDTO.builder()
                    .empNo(user.getNum())
                    .displayName(user.getUsername())
                    .build());
        }
        return result;
    }

    /**
     * 用户编号 → 用户名映射（审计字段回显用）。
     */
    public Map<String, String> userDisplayNameMap() {
        return userQueryService.listDisplayNameMap();
    }
}
