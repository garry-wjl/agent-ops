package ink.garry.rd.agent.ws.infra.common.client.cloudbus;

import ink.garry.rd.agent.ws.infra.common.client.cloudbus.dto.DepartmentDTO;
import ink.garry.rd.agent.ws.infra.common.client.cloudbus.dto.EmployeeDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 原 CloudBus 通讯录客户端（已去掉 {@code dava-gateway-core} 依赖）。
 * <p>
 * 当前为占位实现：不发起任何远程调用，一律返回 {@code null}。
 * 后续可替换为其它通讯录实现。
 */
@Slf4j
@Component
public class CloudBusClient {

    /**
     * 搜索员工（占位：不执行，返回 {@code null}）。
     *
     * @param keyword 关键词
     * @param size    数量
     * @return 恒为 {@code null}
     */
    public List<EmployeeDTO> searchEmployee(String keyword, Integer size) {
        log.debug("CloudBusClient.searchEmployee stubbed, keyword={}, size={}", keyword, size);
        return null;
    }

    /**
     * 搜索部门（占位：不执行，返回 {@code null}）。
     *
     * @param keyword 关键词
     * @param size    数量
     * @return 恒为 {@code null}
     */
    public List<DepartmentDTO> searchDepartment(String keyword, Integer size) {
        log.debug("CloudBusClient.searchDepartment stubbed, keyword={}, size={}", keyword, size);
        return null;
    }
}
