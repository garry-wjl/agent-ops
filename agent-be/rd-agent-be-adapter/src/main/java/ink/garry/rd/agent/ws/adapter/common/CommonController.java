package ink.garry.rd.agent.ws.adapter.common;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.attachment.query.AttachmentQueryService;
import ink.garry.rd.agent.ws.application.common.CommonService;
import ink.garry.rd.agent.ws.client.common.employee.EmployeeProfileVO;
import ink.garry.rd.agent.ws.client.common.employee.EmployeeSearchParamDTO;
import ink.garry.rd.agent.ws.client.common.oss.OssFileAccessUrlVO;
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
 * 通用横切控制器：OSS 文件凭证 / 访问等前端通用能力入口。
 * <p>
 * <b>不感知工作空间</b>：本路径不挂 {@code WorkspaceContextInterceptor}，也不读取
 * {@code X-Workspace-Num}。聊天附件登记发生在 Open/Debug Invoke（有空间上下文）侧。
 */
@RestController
@RequestMapping("/api/v1/common")
@RequiredArgsConstructor
public class CommonController extends BaseController {

    private final CommonService commonService;
    private final AttachmentQueryService attachmentQueryService;

    /**
     * 申请 OSS 上传凭证（纯预签名，不登记 chat_attachment）。
     *
     * @param param 前端入参
     * @return 上传凭证 VO
     */
    @PostMapping("/oss/sts-init")
    public Result<OssPresignResultVO> stsInit(@Valid @RequestBody OssStsInitParam param) {
        OssPresignResultDTO ossPresignResultDTO = commonService.requestStsCredential(param.getFileName());
        return ok(CommonAssembler.toOssPresignResultVO(ossPresignResultDTO));
    }

    /**
     * 取文件访问 URL。
     * <p>未登记对象直接换签（技能包等）；已登记聊天附件按「当前登录用户是否属于附件所属空间」做 ACL，
     * 不依赖请求头空间。
     *
     * @param param 前端入参；必含 {@code fileId}
     * @return 访问地址 VO
     */
    @PostMapping("/oss/file-url")
    public Result<OssFileAccessUrlVO> fileUrl(@Valid @RequestBody OssFileUrlParam param) {
        String fileUrl = attachmentQueryService.getDownloadUrlForCurrentUser(param.getFileId());
        OssFileAccessUrlVO vo = new OssFileAccessUrlVO();
        vo.setUrl(fileUrl);
        return ok(vo);
    }

    /**
     * 工号 / 姓名搜员工（通用通讯录搜索）。
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
     * 用户编号 → 用户名映射（审计字段回显用）。
     */
    @GetMapping("/users/display-map")
    public Result<Map<String, String>> userDisplayMap() {
        return ok(commonService.userDisplayNameMap());
    }
}
