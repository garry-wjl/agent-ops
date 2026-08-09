package ink.garry.rd.agent.ws.adapter.user.controller;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.user.assembler.UserVoAssembler;
import ink.garry.rd.agent.ws.application.user.UserQueryService;
import ink.garry.rd.agent.ws.client.user.dto.UserBriefDTO;
import ink.garry.rd.agent.ws.client.user.param.UserPageQueryParam;
import ink.garry.rd.agent.ws.client.user.vo.UserBriefVO;
import ink.garry.rd.agent.ws.client.user.vo.UserDetailVO;
import ink.garry.rd.agent.ws.client.user.vo.UserVO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户查询接口。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserQueryController extends BaseController {

    @Resource
    private UserQueryService userQueryService;
    @Resource
    private UserVoAssembler assembler;

    @GetMapping("/page")
    public Result<PageVO<UserVO>> page(UserPageQueryParam query) {
        return ok(assembler.toPageVO(userQueryService.pageUsers(assembler.toPageDTO(query))));
    }

    @GetMapping("/detail")
    public Result<UserDetailVO> detail(@RequestParam("num") String num) {
        return ok(assembler.toDetailVO(userQueryService.getUser(num)));
    }

    @GetMapping("/search-enabled")
    public Result<List<UserBriefVO>> searchEnabled(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false) Integer limit) {
        List<UserBriefDTO> list = userQueryService.searchEnabledUsers(keyword, limit);
        return ok(list.stream().map(assembler::toBriefVO).collect(Collectors.toList()));
    }
}
