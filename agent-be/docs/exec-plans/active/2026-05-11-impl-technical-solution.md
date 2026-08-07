# 按技术方案补齐 rd-agent-be 未完成代码

## 范围

依据 `技术方案/` 下 2026-05-11 系列方案，按 facade → client → domain → infra → application → adapter 顺序补齐 rd-agent-be 当前缺失实现，优先补 skill 与 evaluation 主链路，保留方案明确标注 M3 的 ACP/MCP/A2A/API Runner 延后实现。

## 执行步骤

1. facade：补齐领域事件、实体基类 DTO/接口与 skill/evaluation/session/agent facade 契约。
2. client：补齐 skill/evaluation/session 所需 Param、Query、VO。
3. domain：补齐 skill/evaluation 聚合、版本/草稿/用例/种子、repository/gateway/factory/valueobject。
4. infra：补齐 MyBatis entity/mapper/repository/gateway、数据库迁移脚本与 JSON schema 工具接入。
5. application：补齐 skill/evaluation 命令、查询、同步/评测执行服务。
6. adapter：补齐 REST Controller，并复用现有 Result、Trace、UserContext、异常处理风格。
7. 验证：执行 Maven 编译/测试命令，修复编译和测试失败。
8. 收尾：更新 ontology 交付记录。

## 进度

- [x] 解析技术方案与现有代码结构
- [ ] facade
- [ ] client
- [ ] domain
- [ ] infra
- [ ] application
- [ ] adapter
- [ ] 验证
- [ ] ontology
