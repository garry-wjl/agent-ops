# references/ — 外部依赖文档(LLM 友好版)

> 这个目录存放 rd-agent-be 用到的外部库 / 框架 / 工具的"LLM 友好版"文档。
> 让 agent 不用上网就能直接 grep 完整 API 参考。

---

## 命名约定

按 [llms.txt 标准](https://llmstxt.org/):

```
<library-name>-llms.txt        # 完整文档的扁平化版本
<library-name>-llms-full.txt   # 含示例与扩展内容(可选)
```

## 当前收集的参考文档(规划)

| 库 | 用途 | 文档 | 来源 / 更新日期 |
|----|------|------|--------------|
| Spring Boot | 框架核心 | <!-- TODO: spring-boot-llms.txt --> | TODO |
| MyBatis-Plus | ORM | <!-- TODO: mybatis-plus-llms.txt --> | TODO |
| SpringDoc OpenAPI | API 文档 | <!-- TODO --> | TODO |
| Spotless | 格式化 | <!-- TODO --> | TODO |

## 业界对照(用于架构决策时参考)

> 这些不是直接依赖,是设计借鉴对象。可以放设计文档摘要,不需要完整 LLM 文档。

| 系统 | 借鉴点 | 文档 |
|------|-------|------|
| Dify console-api / plugin-daemon | Control Plane / Data Plane 拆分 | <!-- TODO: 摘要笔记 --> |
| LangGraph Server / Workers | thread / run / step 三层模型 | <!-- TODO --> |
| Kubernetes API Server / kubelet | 控制面与执行面的契约设计 | <!-- TODO --> |

## 怎么获取

1. 访问 `<lib-website>/llms.txt` 或 `<lib-website>/llms-full.txt`
2. 没有官方 llms.txt 的,可以用 [Context7](https://context7.com/) 等工具拉取
3. 拉下来后存成 `<lib-name>-llms.txt`,并在上表登记来源 + 日期

## 何时刷新

- 升级该依赖版本时
- 发现 agent 因为文档过时给出错误建议时
- 至少每季度抽查一次主要依赖

---

> **理念**:与其让 agent 在每次任务开始都"猜 API",不如把权威文档预先放进仓库。Repository = System of Record 也适用于第三方知识。
