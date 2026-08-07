package ink.garry.rd.agent.ws.facade.domain;

import cn.hutool.core.lang.Assert;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 领域实体基类。
 * <p>
 * 包含通用审计字段（createNo / updateNo / createTime / updateTime / deleted）。
 * 子类必须实现 domainValidate / save / delete 三个抽象方法。
 * <p>
 * 字段命名按用户决策：使用 createNo / updateNo（而非 createId / updateId）。
 */
@Getter
@Setter
public abstract class DomainEntity {

    /** 主键 ID，由持久层（自增或雪花）分配 */
    protected Long id;
    /** 创建时间；首次 initialize 时赋值，之后不再覆盖 */
    protected LocalDateTime createTime;
    /** 创建人编号；首次 initialize 时赋值，之后不再覆盖 */
    protected String createNo;
    /** 更新时间；每次 initialize 都会刷新为当前时间 */
    protected LocalDateTime updateTime;
    /** 最后更新人编号；每次 initialize 都会刷新为传入的 operatorId */
    protected String updateNo;
    /** 逻辑删除标识：0 未删除，1 已删除 */
    protected Integer deleted;

    /**
     * 初始化审计字段。
     * 首次调用时同时初始化 createTime / createNo，后续调用仅刷新 updateTime / updateNo。
     *
     * @param operatorId 当前操作人 ID
     */
    public void initialize(String operatorId) {
        LocalDateTime now = LocalDateTime.now();
        this.createTime = this.createTime == null ? now : this.createTime;
        this.createNo = this.createNo == null ? operatorId : this.createNo;
        this.updateTime = now;
        this.updateNo = operatorId;
        this.deleted = this.deleted == null ? 0 : this.deleted;
    }

    /**
     * 通用完整性校验：审计字段非空后调用 {@link #domainValidate()} 做领域校验。
     */
    public void validate() {
        Assert.notNull(this.createNo, "实体创建人不能为空");
        Assert.notNull(this.updateNo, "实体更新人不能为空");
        Assert.notNull(this.createTime, "实体创建时间不能为空");
        Assert.notNull(this.updateTime, "实体更新时间不能为空");
        domainValidate();
    }

    /**
     * 领域专属完整性校验，由子类实现。
     */
    public abstract void domainValidate();

    /**
     * 持久化当前实体（六步顺序中的第 5 步）。
     *
     * @param operatorId 当前操作人 ID，用于审计与事件 sender
     */
    public abstract void save(String operatorId);

    /**
     * 删除当前实体并发布删除事件。
     *
     * @param operatorId 当前操作人 ID，用于审计与事件 sender
     */
    public abstract void delete(String operatorId);
}
