# references/ — 外部依赖文档(LLM 友好版)

> 这个目录存放 rd-agent-fe 用到的外部库 / 框架 / 工具的"LLM 友好版"文档。
> 让 agent 不用上网就能直接 grep 完整 API 参考。

---

## 命名约定

按 [llms.txt 标准](https://llmstxt.org/):

```
<library-name>-llms.txt        # 完整文档的扁平化版本
<library-name>-llms-full.txt   # 含示例与扩展内容(可选)
```

## 当前收集的参考文档(规划)

| 库                   | 用途                        | 文档                                                                                                                | 来源 / 更新日期 |
| -------------------- | --------------------------- | ------------------------------------------------------------------------------------------------------------------- | --------------- |
| React 19             | 框架核心                    | <!-- TODO: react-llms.txt -->                                                                                       | TODO            |
| Ant Design 6         | UI 库                       | <!-- TODO: antd-llms.txt -->                                                                                        | TODO            |
| Vite 7               | 构建                        | <!-- TODO -->                                                                                                       | TODO            |
| TypeScript           | 类型                        | <!-- TODO -->                                                                                                       | TODO            |
| TanStack Query / SWR | 数据获取(待选)              | <!-- TODO -->                                                                                                       | TODO            |
| Zustand / Jotai      | 状态管理(待选)              | <!-- TODO -->                                                                                                       | TODO            |
| Monaco Editor        | prompt 编辑器 + JSON 编辑器 | <!-- TODO -->                                                                                                       | TODO            |
| Sentry SDK (browser) | 错误上报                    | <!-- TODO -->                                                                                                       | TODO            |
| Figma(原型设计参考)  | 设计真相                    | [hoQIHy1FcQdfE49oDsiSV9 - AgentSphere](https://www.figma.com/design/hoQIHy1FcQdfE49oDsiSV9/AgentSphere?node-id=0-1) | 在线访问,不下载 |

## 业界对照

> 借鉴的产品形态,不是直接依赖。

| 产品           | 借鉴点                          | 文档                    |
| -------------- | ------------------------------- | ----------------------- |
| ChatGPT 桌面   | 调试台对话流 UI                 | <!-- TODO: 截图笔记 --> |
| Claude desktop | 流式光标动画 + 多轮 thread 切换 | <!-- TODO -->           |
| Dify 控制台    | 提示词管理 / 智能体浏览参考     | <!-- TODO -->           |
| LangSmith      | runs / steps timeline 视觉参考  | <!-- TODO -->           |

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
