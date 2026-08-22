# Agent 应用评测 P1/P2 落地计划

## 状态：完成（2026-08-22）

## 验证
- `mvn test`：192 tests / 0 failures
- `smoke-agent-eval.sh`：**66 pass / 0 fail**
- `pnpm typecheck`：通过（FE）

## 已交付
### P1
- [x] LLM 评估器 createLlm / LlmGraderRunner / CompositeGraderEngine
- [x] 工具向预置 TOOL_CALLED / TOOL_NAME_CONTAINS
- [x] rerunFailed / saveLabels / stats / cancel / NONE
- [x] export / appendFromDebug / importFromSessions
- [x] FE：LLM/Code 创建、标注、导出、回流、重跑、stats

### P2
- [x] CODE SpEL 评估器
- [x] distillFromTask
- [x] 轨迹摘要 / sessionKey 多轮
- [x] 发布门禁钩子（默认关闭）
