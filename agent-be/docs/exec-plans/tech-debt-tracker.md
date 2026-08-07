# Tech Debt Tracker — 技术债清单

> 持续维护已知技术债。每条债务都应该是"可执行"的——能描述清楚问题、影响、和修复路径。

---

## 评级

| 优先级 | 含义 | 处置 SLA |
|--------|------|---------|
| **P0** | 阻塞业务 / 安全风险 / 数据完整性 | 立即处理(≤1 周) |
| **P1** | 显著影响开发效率或用户体验 | 当前迭代内处理 |
| **P2** | 已知瑕疵但不紧急 | 排进 backlog |
| **P3** | 长期改进项 | 见缝插针 |

---

## 当前债务(已知 / 来自 PRD)

| ID | 描述 | 优先级 | 影响范围 | 建议方案 | 发现日期 | 负责人 | 状态 |
|----|------|-------|---------|---------|---------|-------|------|
| D-001 | be ↔ sphere 无服务间鉴权,靠 K8s NetworkPolicy | P1 | 安全 | v0.2 升级 mTLS / 服务间 token | 2026-05-09 | TODO | open(已接受 v0.5 现状) |
| D-002 | Apollo namespace 待 ops 确认 | P1 | 部署 | 启动前 close,见 PRD §9 | 2026-05-09 | ops | open |
| D-003 | 域名待 ops 分配 | P1 | 部署 | 启动前 close,见 PRD §9 | 2026-05-09 | ops | open |
| D-004 | 审计日志保留期待安全合规确认 | P2 | 合规 | 建议 1 年,见 PRD §9 | 2026-05-09 | TODO | open |
| D-005 | 系统设置菜单(用户角色 UI)v0.2 才做 | P2 | 产品 | 后端 user/role 表先就位,UI 留后 | 2026-05-09 | - | accepted |
| D-006 | Runs 全局列表页未做(MVP 仅按 URL 直访) | P3 | 产品 | v0.2+,见 PRD US-4 / §7.2 | 2026-05-09 | - | accepted |
| D-007 | 模型管理优化测试覆盖不足(8 项仅 1 项:安全回归/权限集成/序列化/运行时解析等缺失) | P0 | 全栈安全 | 补 7 类测试,见 [跟进计划](active/2026-06-17-model-management-followup.md#p0--阻塞交付) | 2026-06-17 | TBD | open(本轮已补安全回归/scope/凭证/去重/脱敏共 5 类,Flyway+权限集成+Agent 端到端待补) |
| D-008 | `normalizeSnapshot` 缺 refs 去重逻辑 | P1 | agent 发布/运行时 | resolveSkillRefs/resolveToolRefs 按 num+versionNum dedup + 单测 | 2026-06-17 | TBD | ✅ 偿还(2026-06-17,dedupSkillRefs/dedupToolRefs + AgentCommandServiceNormalizeTest) |
| D-009 | `ModelUpdateParam`(VO/DTO)缺 scope 字段 | P1 | 模型更新契约 | 补字段或加守卫 + 同步方案文档 | 2026-06-17 | TBD | ✅ 偿还(2026-06-17,决策不加 scope,编辑禁变更归属,JavaDoc 注明 + assertWritableByEntry 守卫) |
| D-010 | `ModelCredentialResolver` 零测试 | P1 | 运行时密钥解析 | 补按 num 解密、跨 scope 不串用单测 | 2026-06-17 | TBD | ✅ 偿还(2026-06-17,ModelCredentialResolverTest 5 case) |
| D-011 | `ModelDomainEventDTO` 缺 scope 字段 | P2 | 事件下游消费 | 事件载荷补 scope | 2026-06-17 | TBD | ✅ 偿还(2026-06-17,DTO + from() 补 scope + ModelDomainTest) |
| D-012 | `MODEL_ENABLE` 冗余权限码常量(方案要求 enable/disable 走 model:update) | P2 | 权限契约 | 移除常量或修正路由权限注解 | 2026-06-17 | TBD | ✅ 偿还(2026-06-17,移除常量 + 清理 V28/V29 seed + V29 补收敛 SQL + RouteRoleMapping 走 model:update) |

---

## 已偿还

| ID | 描述 | 偿还日期 | 偿还方式 |
|----|------|---------|---------|
| (暂无) | - | - | - |

---

## 添加新债务的格式

```markdown
| D-NNN | <一句话描述问题> | P0/P1/P2/P3 | <影响哪些模块/场景> | <建议怎么修> | YYYY-MM-DD | <userid> | open |
```

---

> **理念**:技术债像高息贷款。持续小额偿还远比堆积起来再痛苦清理便宜。每个 sprint 应至少偿还 1 条 P1/P2,避免无限累积。
