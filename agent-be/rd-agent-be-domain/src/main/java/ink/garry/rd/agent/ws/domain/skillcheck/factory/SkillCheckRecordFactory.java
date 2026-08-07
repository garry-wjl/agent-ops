package ink.garry.rd.agent.ws.domain.skillcheck.factory;

import cn.hutool.core.lang.Assert;
import ink.garry.rd.agent.ws.domain.skillcheck.SkillCheckRecord;
import ink.garry.rd.agent.ws.domain.skillcheck.gateway.SkillCheckRecordGateway;
import ink.garry.rd.agent.ws.domain.skillcheck.repository.SkillCheckRecordRepository;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckError;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckItemResult;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckResult;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SkillCheckRecord 领域工厂（固定 2 方法）。
 * <ul>
 *   <li>{@link #buildCheckRecord}：用检测结果字段构造一条新的检测记录（未落库）。</li>
 *   <li>{@link #buildCheckRecordByNum}：按业务编号从仓储加载检测记录并装配依赖。</li>
 * </ul>
 * <p>
 * 本类 {@code @Component} 受 Spring 管理；依赖 {@code @Resource} 字段注入。创建出的领域对象
 * 由工厂手动 wire 所需的 Repository / Gateway / EventPublisher，使调用方可直接执行 save / delete。
 */
@Component
public class SkillCheckRecordFactory {

    @Resource
    private SkillCheckRecordRepository skillCheckRecordRepository;
    @Resource
    private SkillCheckRecordGateway skillCheckRecordGateway;
    @Resource
    private DomainEventPublisher domainEventPublisher;

    /**
     * 用检测结果字段构造一条新的检测记录聚合（未落库）。
     * <p>调用方拿到返回对象后立即调用 {@link SkillCheckRecord#save(String)} 落库。
     *
     * @param skillNum            所属 Skill 业务编号
     * @param version             检测的目标版本号
     * @param result              整体检测结果（PASS / FAIL）
     * @param sizeResult          大小检测子结果
     * @param formatResult        格式检测子结果
     * @param availabilityResult  可用性检测子结果
     * @param errors              错误明细列表（可空；工厂内会替换为空集合）
     * @param costMs              检测总耗时（毫秒）
     * @param workspaceNum        归属工作空间业务编号
     * @return 已装配完依赖、可直接 save 的检测记录聚合
     */
    public SkillCheckRecord buildCheckRecord(String skillNum,
                                             String version,
                                             SkillCheckResult result,
                                             SkillCheckItemResult sizeResult,
                                             SkillCheckItemResult formatResult,
                                             SkillCheckItemResult availabilityResult,
                                             List<SkillCheckError> errors,
                                             Long costMs,
                                             String workspaceNum) {
        Assert.notBlank(skillNum, "skillNum 不能为空");
        Assert.notBlank(version, "version 不能为空");
        Assert.notNull(result, "整体检测结果不能为空");

        SkillCheckRecord record = new SkillCheckRecord();
        record.setSkillNum(skillNum);
        record.setVersion(version);
        record.setResult(result);
        record.setSizeResult(sizeResult);
        record.setFormatResult(formatResult);
        record.setAvailabilityResult(availabilityResult);
        record.setErrors(errors == null ? new ArrayList<>() : new ArrayList<>(errors));
        record.setCostMs(costMs);
        record.setWorkspaceNum(workspaceNum);

        wire(record);
        return record;
    }

    /**
     * 按业务编号加载检测记录并装配依赖（等价于 {@code repository.findByNum(num)} + wire）。
     *
     * @param num 检测记录业务编号
     * @return 装配完依赖的检测记录聚合；不存在时返回 {@code null}
     */
    public SkillCheckRecord buildCheckRecordByNum(String num) {
        Assert.notBlank(num, "检测记录业务编号不能为空");
        SkillCheckRecord record = skillCheckRecordRepository.findByNum(num);
        if (record == null) {
            return null;
        }
        wire(record);
        return record;
    }

    // ---- 私有装配 ----

    /** 把 3 个依赖一次性注入检测记录聚合根。 */
    private void wire(SkillCheckRecord record) {
        record.setSkillCheckRecordRepository(this.skillCheckRecordRepository);
        record.setSkillCheckRecordGateway(this.skillCheckRecordGateway);
        record.setDomainEventPublisher(this.domainEventPublisher);
    }
}
