## Summary

<!-- 用 1–3 条说明：改了什么、为什么改 -->

-

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change（不兼容 API / 行为）
- [ ] Documentation
- [ ] Refactor / chore（无行为变化）
- [ ] Tests only

## Scope

- [ ] `agent-be`
- [ ] `agent-fe`
- [ ] 根文档 / `.github` / 其它

## Architecture checklist

- [ ] 未破坏 Control Plane / Data Plane 边界（be 不执行 Agent；fe 不直连 sphere）
- [ ] 后端六层依赖未越界（如有 be 改动）
- [ ] 跨端契约变更已两边同步（如适用）
- [ ] 写操作可审计（如适用）

## Test plan

<!-- 列出你已验证或希望 reviewer 验证的步骤 -->

- [ ] `cd agent-be && mvn test`（或说明跳过原因）
- [ ] `cd agent-fe && pnpm typecheck && pnpm test`（或说明跳过原因）
- [ ] UI 主流程手工验证（如适用）
- [ ] 新增 / 更新了单元测试（业务代码改动时必选）

## Screenshots / recordings

<!-- UI 改动请附截图或短视频；无则写 N/A -->

## Risk & rollback

<!-- 潜在风险、灰度建议、如何回滚 -->

-

## Related issues

<!-- 例如：Closes #123 -->

- 
