package ink.garry.rd.agent.ws.application.user;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.client.auth.constant.AuthzConstants;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.user.constant.UserConstants;
import ink.garry.rd.agent.ws.client.user.dto.UserBriefDTO;
import ink.garry.rd.agent.ws.client.user.dto.UserDTO;
import ink.garry.rd.agent.ws.client.user.dto.UserDetailDTO;
import ink.garry.rd.agent.ws.client.user.dto.UserPageQueryParamDTO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.auth.userrolebinding.entity.UserRoleBindingEntity;
import ink.garry.rd.agent.ws.infra.auth.userrolebinding.mapper.UserRoleBindingMapper;
import ink.garry.rd.agent.ws.infra.user.entity.UserEntity;
import ink.garry.rd.agent.ws.infra.user.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User 读侧应用服务。
 */
@Service
public class UserQueryService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private UserRoleBindingMapper userRoleBindingMapper;

    /**
     * 分页查询用户。
     *
     * @param param 查询条件
     * @return 分页 DTO
     */
    public PageVO<UserDTO> pageUsers(UserPageQueryParamDTO param) {
        Assert.notNull(param, "查询参数不能为空");
        int pageNo = (param.getPageNo() == null || param.getPageNo() < 1) ? 1 : param.getPageNo();
        int pageSize = (param.getPageSize() == null || param.getPageSize() < 1) ? 20 : param.getPageSize();

        LambdaQueryWrapper<UserEntity> wrapper = Wrappers.<UserEntity>lambdaQuery()
                .eq(StrUtil.isNotBlank(param.getStatus()), UserEntity::getStatus, param.getStatus())
                .and(StrUtil.isNotBlank(param.getKeyword()), w -> w
                        .like(UserEntity::getUsername, param.getKeyword())
                        .or()
                        .like(UserEntity::getEmail, param.getKeyword()))
                .orderByDesc(UserEntity::getUpdateTime);

        IPage<UserEntity> page = userMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<UserDTO> list = page.getRecords().stream().map(this::toDTO).collect(Collectors.toList());
        return PageVO.of(list, page.getTotal(), pageNo, pageSize);
    }

    /**
     * 用户详情（含平台角色）。
     *
     * @param num 用户业务编号
     * @return 详情 DTO
     */
    public UserDetailDTO getUser(String num) {
        Assert.notBlank(num, "用户编号不能为空");
        UserEntity entity = userMapper.findByNum(num);
        if (entity == null) {
            throw new BusinessException(BizCode.USER_NOT_FOUND.getCode(), BizCode.USER_NOT_FOUND.getMessage());
        }
        UserDetailDTO dto = new UserDetailDTO();
        dto.setNum(entity.getNum());
        dto.setUsername(entity.getUsername());
        dto.setEmail(entity.getEmail());
        dto.setRemark(entity.getRemark());
        dto.setStatus(entity.getStatus());
        dto.setPlatformRoleNums(listPlatformRoleNums(num));
        return dto;
    }

    /**
     * 按用户名查业务编号（登录 / 身份解析）。
     *
     * @param username 登录用户名
     * @return num；不存在返回 null
     */
    public String findNumByUsername(String username) {
        if (StrUtil.isBlank(username)) {
            return null;
        }
        UserEntity entity = userMapper.findByUsername(username.trim());
        return entity == null ? null : entity.getNum();
    }

    /**
     * 按 num 查用户名。
     *
     * @param num 用户业务编号
     * @return username；不存在返回 null
     */
    public String findUsernameByNum(String num) {
        if (StrUtil.isBlank(num)) {
            return null;
        }
        UserEntity entity = userMapper.findByNum(num);
        return entity == null ? null : entity.getUsername();
    }

    /**
     * 启用用户搜索（选人）。
     *
     * @param keyword 关键字
     * @param limit   条数上限
     * @return 简要列表
     */
    public List<UserBriefDTO> searchEnabledUsers(String keyword, Integer limit) {
        int lim = (limit == null || limit < 1)
                ? UserConstants.SEARCH_DEFAULT_LIMIT
                : Math.min(limit, UserConstants.SEARCH_MAX_LIMIT);
        List<UserEntity> rows = userMapper.searchEnabled(StrUtil.blankToDefault(keyword, ""), lim);
        List<UserBriefDTO> result = new ArrayList<>();
        for (UserEntity row : rows) {
            UserBriefDTO dto = new UserBriefDTO();
            dto.setNum(row.getNum());
            dto.setUsername(row.getUsername());
            dto.setEmail(row.getEmail());
            result.add(dto);
        }
        return result;
    }

    /**
     * 全量用户显示名映射（num → username，并附带 username → username 兼容键）。
     * <p>供前端审计字段（创建人 / 更新人）回显；不含邮箱等敏感字段。
     * 接口对已登录用户开放（挂在 common），不要求 user_manage:read。
     */
    public Map<String, String> listDisplayNameMap() {
        Map<String, String> map = new HashMap<>();
        List<UserEntity> rows = userMapper.listAllNumAndUsername();
        for (UserEntity e : rows) {
            if (e == null || StrUtil.isBlank(e.getNum()) || StrUtil.isBlank(e.getUsername())) {
                continue;
            }
            map.put(e.getNum(), e.getUsername());
            map.put(e.getUsername(), e.getUsername());
        }
        return map;
    }

    /**
     * 批量解析显示名：优先按 num，其次按 username（兼容旧成员 ID）。
     *
     * @param ids num 或 username 集合
     * @return id → username
     */
    public Map<String, String> resolveDisplayNames(Collection<String> ids) {
        Map<String, String> map = new HashMap<>();
        if (CollUtil.isEmpty(ids)) {
            return map;
        }
        Set<String> keys = ids.stream().filter(StrUtil::isNotBlank).collect(Collectors.toCollection(LinkedHashSet::new));
        if (keys.isEmpty()) {
            return map;
        }
        List<UserEntity> byNum = userMapper.listByNums(keys);
        for (UserEntity e : byNum) {
            map.put(e.getNum(), e.getUsername());
        }
        Set<String> unresolved = keys.stream().filter(k -> !map.containsKey(k)).collect(Collectors.toSet());
        if (!unresolved.isEmpty()) {
            List<UserEntity> byName = userMapper.listByUsernames(unresolved);
            for (UserEntity e : byName) {
                map.put(e.getUsername(), e.getUsername());
                map.putIfAbsent(e.getNum(), e.getUsername());
            }
        }
        for (String key : keys) {
            map.putIfAbsent(key, key);
        }
        return map;
    }

    private List<String> listPlatformRoleNums(String userNum) {
        UserRoleBindingEntity binding = userRoleBindingMapper.findByUserAndWorkspace(
                userNum, AuthzConstants.PLATFORM_WORKSPACE_NUM);
        if (binding == null) {
            return new ArrayList<>();
        }
        var domain = UserRoleBindingEntity.toDomain(binding);
        if (domain == null || CollUtil.isEmpty(domain.getRoleNums())) {
            return new ArrayList<>();
        }
        return new ArrayList<>(domain.getRoleNums());
    }

    private UserDTO toDTO(UserEntity entity) {
        UserDTO dto = new UserDTO();
        dto.setNum(entity.getNum());
        dto.setUsername(entity.getUsername());
        dto.setEmail(entity.getEmail());
        dto.setRemark(entity.getRemark());
        dto.setStatus(entity.getStatus());
        return dto;
    }
}
