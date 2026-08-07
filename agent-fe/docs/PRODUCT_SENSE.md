# Product Sense — 产品品味与取舍偏好

> 这份文件让 agent 在做产品判断时知道"我们的偏好是什么"。
> 不写在这里的,agent 会按训练集里的"业界平均"来做决定,常常和你不一致。

---

## 1. 我们解决谁的什么问题

- **主要用户画像**(详见 PRD-MVP §3.1):
  - **Agent 开发者**(editor):写新 agent → 调试 → 调 prompt
  - **算法 / PM**(editor):编辑 prompt → 真实 jira_key 评估 → 对比版本 → activate
  - **运维 / SRE**(viewer):查 agent 状态 / prompt 生效版本 / 审计日志
- **核心 use case**(PRD-MVP §3.2):
  - US-1:编辑 prompt → 保存 → 切到调试台 → 跑 → 看新输出(< 5 步操作 < 30 秒)
  - US-2:智能体管理列表一屏看到所有 agent + 当前 prompt 版本 badge
  - US-3:选 agent → 输入 → 流式回复(thinking / tool_use / tool_result step 实时展开)→ 多轮追问形成 thread
  - US-4:URL 直访 `/runs/{run_id}` 查 step timeline
  - US-5:版本历史一键回滚到上一个版本
- **不服务的用户**:
  - **工分系统的最终用户**(研发工程师 / TL / PM)— 他们用 rd-points-fe
  - **外部第三方** — 仅内网产品

## 2. 决策原则

- **设计还原优先**:任何与 Figma 不一致的实现都需要在 PR 说明理由,不允许"差不多就行"
- **扁平化优先**:有边框 / 阴影 / 内部分隔线的方案,默认拒绝(已是设计共识)
- **优先复用,再考虑新建**:架构对齐 rd-points-fe;新组件先 grep 是否已有;已有的不够好就改原件而不是 fork
- **空状态比报错更友好**:能展示空状态就不要弹错误对话框(如智能体列表为空 / 没有 thread / prompt 暂未编辑)
- **关键操作必须二次确认**:prompt activate / rollback / 删除都需要弹窗确认,不能裸 click 触发
- **错误信息面向用户而非面向开发**:不抛"NetworkError"给最终用户;统一"加载失败,请稍后重试"+ 展开按钮看技术细节
- **配置项越少越好**:每个新增设置项都要回答"为什么不能用合理默认值"
- **不为'可能用得到'做产品设计**:除非 PRD 明确写了,不实现额外能力(MVP 严格按 PRD §7.1 范围)

## 3. 我们不会做 X,因为 Y

| 不做的                                          | 原因                                                     | 何时重新评估          |
| ----------------------------------------------- | -------------------------------------------------------- | --------------------- |
| 拖拽 / 复杂手势交互                             | 桌面后台产品,鼠标点击为主                                | 出现明确诉求          |
| 主题切换(暗黑模式)                              | MVP 仅亮色;算法/PM 用户没需求                            | v0.3+                 |
| 移动端适配                                      | 桌面 only(PRD §7.2)                                      | 不重新评估            |
| 多人协同编辑 prompt                             | MVP 接受"最后写的覆盖前面"                               | v0.3,如果有冲突投诉   |
| 调试台输出可视化(图表 / 思维导图)               | sphere step 是文本流,可视化在 MVP 增加复杂度而无用户价值 | 出现明确场景          |
| 调试台保存"对话 + 输出"为业务 fixture(自动评测) | MVP 不做 regression suite                                | v0.2+                 |
| 流式输出用 SSE / WebSocket                      | 沿用 SphereDebug 轮询(1.5s)成熟方案                      | sphere 升级流式协议时 |

## 4. UI / UX 偏好

> 完整 UI 规范见 [docs/FRONTEND.md](FRONTEND.md);这里只列产品偏好。

- 关键操作必须有二次确认(prompt activate / rollback / 删除)
- 表格默认显示前 20 条,超出用分页(不用无限滚动)
- 顶部 breadcrumb 一直可见,不允许"消失"
- 流式输出文本逐字出现,末尾蓝色光标 `|` 闪烁(沿用 SphereDebug)
- 空状态必须有插画 + 引导文案 + CTA(如智能体列表为空 → "暂无 agent,请联系 sphere 团队接入")
- 错误状态:5xx 显示"服务暂时不可用,刷新重试" + 二级展开看 request_id;403 显示"无权限,请联系管理员"
- 颜色语义(沿用 PRD §4.2 调试台色板):
  - 主色 `#2B52D9`、文字 5 档 `#0F172B → #90A1B9`
  - text 蓝 `#2B52D9` / thinking 紫 `#7C3AED` / tool_use 绿 `#10B981` / tool_result 橙 `#F59E0B`
  - 状态 badge:成功绿 / 警告黄 / 错误红 / 信息蓝(走 Antd 默认语义色)

## 5. 文案与措辞

> 与 be 共享术语表见 [`../../rd-agent-ws/docs/CONVENTIONS.md`](../../rd-agent-ws/docs/CONVENTIONS.md)。

- 用"调试台"不用 "playground"
- 用"智能体管理"不用 "agent 列表"
- 用"提示词管理"不用 "prompt 库"
- 用"对话流 / 新对话"不用 "session"
- 用"激活版本"不用 "current/published version"
- 用"代码默认 prompt"不用 "fallback prompt"
- 错误信息以动词开头:"请检查..."而非"出错:..."
- 按钮命名优先动词:"激活"、"回滚"、"复制"、"重新生成",而非"OK"、"确定"

## 6. 性能偏好

> 详细 SLO 见 [docs/RELIABILITY.md](RELIABILITY.md)。

- 首屏 LCP < 2s(局域网,PRD §5.1)
- 调试台 step 轮询固定 1.5s,**禁止**出于"看起来更流畅"而缩短间隔(浪费后端)
- 列表加载延迟 > 500ms 必须显示骨架屏
- 提示词编辑器 > 1000 行内容时启用 Monaco 的虚拟滚动(默认开启即可)
- bundle size:首屏 JS gzipped < 300KB,超出必须 code-split

---

> **提示**:每次 PR review 中提到的"我喜欢/我不喜欢"都应该被沉淀到本文件。否则下次 agent 还会犯同样的错。
