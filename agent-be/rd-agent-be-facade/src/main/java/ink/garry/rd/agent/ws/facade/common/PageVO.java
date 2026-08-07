package ink.garry.rd.agent.ws.facade.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页 VO 通用结构。
 * 跨能力共享的列表返回结构，承载列表数据与分页元信息。
 *
 * @param <T> 列表元素类型
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PageVO<T> {
    /** 总记录数 */
    private Long total;
    /** 当前页数据列表 */
    private List<T> list;
    /** 当前页码（从 1 起） */
    private Integer pageNo;
    /** 每页大小 */
    private Integer pageSize;

    /**
     * 构造非空分页 VO。
     *
     * @param list     当前页数据
     * @param total    总记录数
     * @param pageNo   当前页码
     * @param pageSize 每页大小
     * @param <T>      列表元素类型
     * @return 填充完成的分页 VO
     */
    public static <T> PageVO<T> of(List<T> list, Long total, Integer pageNo, Integer pageSize) {
        return PageVO.<T>builder().list(list).total(total).pageNo(pageNo).pageSize(pageSize).build();
    }

    /**
     * 构造空分页 VO（total = 0、list 为空）。
     *
     * @param pageNo   当前页码
     * @param pageSize 每页大小
     * @param <T>      列表元素类型
     * @return 空分页 VO
     */
    public static <T> PageVO<T> empty(Integer pageNo, Integer pageSize) {
        return of(List.of(), 0L, pageNo, pageSize);
    }
}
