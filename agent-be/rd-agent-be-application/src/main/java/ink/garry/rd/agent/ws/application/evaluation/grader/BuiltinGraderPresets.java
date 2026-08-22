package ink.garry.rd.agent.ws.application.evaluation.grader;

import ink.garry.rd.agent.ws.client.evaluation.grader.GraderPresetVO;
import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.BuiltinGraderCode;

import java.util.Arrays;
import java.util.List;

/** 平台内置评估器预置目录（代码常量，非 DB 种子）。 */
public final class BuiltinGraderPresets {

    private BuiltinGraderPresets() {
    }

    /**
     * 预置目录。
     *
     * @return 预置列表
     */
    public static List<GraderPresetVO> list() {
        return Arrays.asList(
                new GraderPresetVO(BuiltinGraderCode.NON_EMPTY.name(), "输出非空",
                        "检查 Agent 输出非空", "{}"),
                new GraderPresetVO(BuiltinGraderCode.EXACT_MATCH.name(), "精确匹配",
                        "与 reference 精确匹配（可 trim/ignoreCase）",
                        "{\"trim\":true,\"ignoreCase\":false}"),
                new GraderPresetVO(BuiltinGraderCode.CONTAINS.name(), "包含关键词",
                        "输出包含全部 keywords", "{\"keywords\":[]}"),
                new GraderPresetVO(BuiltinGraderCode.JSON_VALID.name(), "合法 JSON",
                        "输出为合法 JSON 对象或数组", "{}"),
                new GraderPresetVO(BuiltinGraderCode.TOOL_CALLED.name(), "工具已调用",
                        "检查 Agent 轨迹中是否调用了工具", "{}"),
                new GraderPresetVO(BuiltinGraderCode.TOOL_NAME_CONTAINS.name(), "工具名含关键词",
                        "轨迹 toolNames 包含指定关键词", "{\"keyword\":\"\"}")
        );
    }

    /**
     * 按编码取预置；不存在返回 null。
     *
     * @param presetCode 预置编码
     * @return 预置或 null
     */
    public static GraderPresetVO require(String presetCode) {
        return list().stream()
                .filter(p -> p.getPresetCode().equals(presetCode))
                .findFirst()
                .orElse(null);
    }
}
