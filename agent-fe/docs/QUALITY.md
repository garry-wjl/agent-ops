# 质量自检

> 每次提交前自检；CI 上自动执行。所有项必须通过才算"完成"。

## 验证矩阵

| 维度     | 命令                        | 通过标准                    |
| -------- | --------------------------- | --------------------------- |
| 代码风格 | `pnpm lint`                 | 0 error，warning ≤ 历史值   |
| 样式检查 | `pnpm lint:style`           | 0 error                     |
| 格式化   | `pnpm format:check`         | 0 diff                      |
| 类型检查 | `pnpm build`（含 `tsc -b`） | 0 error                     |
| 构建     | `pnpm build`                | 0 error；产物大小未异常增长 |
| 提交信息 | commitlint                  | 符合 conventional commits   |

<!-- TODO: 引入 Vitest 后补充单元测试和覆盖率行 -->

## 评分维度（人工 review 时参考）

### 代码质量

- 命名清晰（变量/函数/组件名能自解释）
- 单文件 ≤ 300 行（超过就拆）
- 复用提取合理（>3 处重复就抽组件/hook/util）
- Props 类型完整（不用 `any`，用具体 type/interface）
- 类型导入用 `import type`

### 架构合规

- 不绕过 `services/request.ts` 直接 import axios
- 全局状态走 Zustand store，选择式订阅
- API 调用通过 `services/` 层，不在组件中直接请求
- 图标在 `components/IconRenderer/registry.ts` 注册后使用
- 跨模块类型放 `src/types/`

### 用户体验

- Loading / Empty / Error 三态完整
- 危险操作有确认弹窗
- 核心用户界面文案走 i18n
- 最低支持宽度 1280px，无意外水平滚动条

### 安全

- 无硬编码密钥
- 无未 sanitize 的 `dangerouslySetInnerHTML`
- 用户输入未拼接到 URL/HTML 中

## 自检清单（每次 push 前）

```bash
pnpm lint:fix                 # 自动修可修的
pnpm lint:style:fix           # 样式自动修复
pnpm format                   # 格式化
pnpm build                    # 构建 + 类型检查
git diff --staged             # 自检敏感信息
git log -1 --pretty=%B        # 检查 commit message 格式
```

任何一项失败 → 修完再 push。**不要把脏代码留给 CI**。
