package ink.garry.rd.agent.ws.adapter.common;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.common.CommonService;
import ink.garry.rd.agent.ws.client.common.employee.EmployeeProfileVO;
import ink.garry.rd.agent.ws.client.common.employee.EmployeeSearchParamDTO;
import ink.garry.rd.agent.ws.client.common.oss.OssFileAccessUrlVO;
import ink.garry.rd.agent.ws.client.common.oss.OssFileDeleteParam;
import ink.garry.rd.agent.ws.client.common.oss.OssFileUrlParam;
import ink.garry.rd.agent.ws.client.common.oss.OssPresignResultVO;
import ink.garry.rd.agent.ws.client.common.oss.OssStsInitParam;
import ink.garry.rd.agent.ws.facade.common.Result;
import ink.garry.rd.agent.ws.infra.common.client.oss.dto.OssPresignResultDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通用横切控制器：OSS 文件凭证 / 访问 / 删除等前端通用能力入口。
 * <p>
 * 不归属任何业务领域（auth / session / agent / ...），仅做 {@link CommonService} 的薄入口；
 * 所有响应统一走 {@link BaseController#ok(Object)} 包 {@link Result}。
 */
@RestController
@RequestMapping("/api/v1/common")
@RequiredArgsConstructor
public class CommonController extends BaseController {

    private final CommonService commonService;

    /**
     * 申请 OSS / S3 / 内网 token 上传凭证。
     *
     * @param param 前端入参
     * @return 上传凭证 VO；前端按 {@code cloudName} 选 SDK 直传
     */
    @PostMapping("/oss/sts-init")
    public Result<OssPresignResultVO> stsInit(@Valid @RequestBody OssStsInitParam param) {
        OssPresignResultDTO ossPresignResultDTO = commonService.requestStsCredential(param.getFileName());
        return ok(CommonAssembler.toOssPresignResultVO(ossPresignResultDTO));
    }

    /**
     * 取文件访问 URL（源文件 + 预览）。
     *
     * @param param 前端入参；必含 {@code fileId}
     * @return 访问地址 VO
     */
    @PostMapping("/oss/file-url")
    public Result<OssFileAccessUrlVO> fileUrl(@Valid @RequestBody OssFileUrlParam param) {
        String fileUrl = commonService.getFileUrl(param.getFileId());
        OssFileAccessUrlVO vo = new OssFileAccessUrlVO();
        vo.setUrl(fileUrl);
        return ok(vo);
    }

    /**
     * 工号 / 姓名搜员工（通用通讯录搜索）。
     *
     * @param keyword 关键字（工号或姓名，长度 ≥ 2）
     * @param limit   返回条数（可空，默认 20，最大 50）
     * @return 员工档案 Vo 列表
     */
    @GetMapping("/employee/search")
    public Result<List<EmployeeProfileVO>> searchEmployee(@RequestParam("keyword") String keyword,
                                                          @RequestParam(value = "limit", required = false) Integer limit) {
        EmployeeSearchParamDTO param = new EmployeeSearchParamDTO();
        param.setKeyword(keyword);
        param.setLimit(limit);
        List<EmployeeProfileVO> vos = commonService.searchEmployees(param).stream()
                .map(CommonAssembler::toEmployeeProfileVO)
                .collect(Collectors.toList());
        return ok(vos);
    }

    /**
     * 用户编号 → 用户名映射（创建人 / 更新人等审计字段回显）。
     * <p>已登录即可访问；仅返回 num/username，不含邮箱等敏感字段。
     */
    @GetMapping("/users/display-map")
    public Result<Map<String, String>> userDisplayMap() {
        return ok(commonService.userDisplayNameMap());
    }
}
