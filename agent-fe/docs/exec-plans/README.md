# exec-plans/ — 执行计划目录

> 复杂任务的执行计划在这里。索引在 [docs/PLANS.md](../PLANS.md)。

---

## 目录约定

```
exec-plans/
├── README.md                    ← 本文件(说明)
├── tech-debt-tracker.md         ← 技术债清单
├── active/                      ← 进行中的计划
│   └── <feature-name>.md
└── completed/                   ← 已完成归档
    └── YYYY-MM-DD-<feature-name>.md
```

## 命名约定

- **active/** 中:用 `<feature-or-task-name>.md`(如 `slice-1-skeleton-and-console.md`、`slice-3-prompts-mgmt.md`)
- **completed/** 中:归档时改名加日期前缀 `YYYY-MM-DD-<原名>.md`

## 何时创建一份 plan

满足以下任一条件就应该创建:

- 涉及 3 个以上文件的修改
- 跨越 2 次以上对话的工作(agent 中途会被换出)
- 任何架构层面的改动
- 任何会引入新依赖的工作
- 任何耗时超过 30 分钟的任务
- 任何 UI 改动需要对齐 Figma 节点 — 把 Figma node id 写在 plan 里

## 当前 active plans(对照 PRD §8 切片)

> 切片 1/2/3 上线前各应有对应 plan 文件,见 [docs/PLANS.md](../PLANS.md) 索引。

## Plan 文件格式

详见 [docs/PLANS.md](../PLANS.md) 的"Plan 模板"小节。

UI 类 plan 额外建议字段:

- **Figma 节点 id**:对应的设计原型节点
- **验收方式**:在浏览器跑通 + 截图对比 Figma

## 完成后的归档流程

1. 把文件从 `active/` 移到 `completed/`
2. 文件名加上 `YYYY-MM-DD-` 前缀(完成日期)
3. 更新 [docs/PLANS.md](../PLANS.md) 的"已完成"表格
4. 把执行过程中产生的设计决策**沉淀**到 `docs/design-docs/` 对应位置
   —— plan 不是知识档案,它执行完就该退役

---

> 不要让 plan 文件无限增长。完成的 plan 必须及时归档,避免和 active 混淆。
