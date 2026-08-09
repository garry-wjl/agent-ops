package ink.garry.rd.agent.ws.adapter.user.controller;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.user.assembler.UserVoAssembler;
import ink.garry.rd.agent.ws.application.user.UserCommandService;
import ink.garry.rd.agent.ws.client.user.param.UserCreateParam;
import ink.garry.rd.agent.ws.client.user.param.UserNumParam;
import ink.garry.rd.agent.ws.client.user.param.UserPlatformRolesParam;
import ink.garry.rd.agent.ws.client.user.param.UserResetPasswordParam;
import ink.garry.rd.agent.ws.client.user.param.UserUpdateParam;
import ink.garry.rd.agent.ws.client.user.vo.UserVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户写接口。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserCommandController extends BaseController {

    @Resource
    private UserCommandService userCommandService;
    @Resource
    private UserVoAssembler assembler;

    @PostMapping("/create")
    public Result<UserVO> create(@Valid @RequestBody UserCreateParam param) {
        return ok(assembler.toVO(userCommandService.createUser(assembler.toCreateDTO(param), getCurrentUserId())));
    }

    @PostMapping("/update")
    public Result<UserVO> update(@Valid @RequestBody UserUpdateParam param) {
        return ok(assembler.toVO(userCommandService.updateUser(assembler.toUpdateDTO(param), getCurrentUserId())));
    }

    @PostMapping("/enable")
    public Result<Void> enable(@Valid @RequestBody UserNumParam param) {
        userCommandService.enableUser(param.getNum(), getCurrentUserId());
        return ok(null);
    }

    @PostMapping("/disable")
    public Result<Void> disable(@Valid @RequestBody UserNumParam param) {
        userCommandService.disableUser(param.getNum(), getCurrentUserId());
        return ok(null);
    }

    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody UserResetPasswordParam param) {
        userCommandService.resetPassword(assembler.toResetDTO(param), getCurrentUserId());
        return ok(null);
    }

    @PostMapping("/save-platform-roles")
    public Result<Void> savePlatformRoles(@Valid @RequestBody UserPlatformRolesParam param) {
        userCommandService.savePlatformRoles(assembler.toRolesDTO(param), getCurrentUserId());
        return ok(null);
    }
}
