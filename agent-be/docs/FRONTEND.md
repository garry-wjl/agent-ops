# Frontend — 前端代码规范

> ⚠️ **rd-agent-be 是纯后端服务,无前端代码。**
>
> 前端规范完全在独立仓库 [`../rd-agent-fe/`](../../rd-agent-fe/) 维护,详见:
> - [../rd-agent-fe/docs/FRONTEND.md](../../rd-agent-fe/docs/FRONTEND.md) — 前端代码规范
> - [../rd-agent-ws/docs/CONVENTIONS.md](../../rd-agent-ws/docs/CONVENTIONS.md) — fe ↔ be 接口契约

---

## 与前端的契约(be 视角)

> 完整契约见 [`../rd-agent-ws/docs/CONVENTIONS.md §2.1`](../../rd-agent-ws/docs/CONVENTIONS.md)。

| 项 | 约定 |
|----|------|
| 协议 | HTTPS |
| 鉴权 | JWT Bearer Token,fe 用 axios 拦截器注入 |
| 路径前缀 | `/admin/api/v1/*` |
| 错误响应 | `{ code: string, message: string, request_id: string }` |
| 时间字段 | UTC ISO 8601(展示时区由 fe 处理) |
| 列表分页 | query string `?page=1&perPage=20`,响应 `{ data: [], total: N, page, perPage }` |
| 流式接口 | sphere step 轮询沿用 SphereDebug 协议 `?seqAfter=...`,fe 间隔 1.5s 主动拉 |

## API 契约维护责任

- **新增 / 修改 / 废弃 API**:必须在本仓库 `docs/generated/api-spec.json`(OpenAPI)记录,并在 PR description 中说明
- **breaking change**:必须先在 [`../rd-agent-ws/docs/CONVENTIONS.md`](../../rd-agent-ws/docs/CONVENTIONS.md) 变更日志登记,通知 fe 团队
- **OpenAPI 自动生成**:由 SpringDoc 在 `/v3/api-docs` 暴露,Swagger UI 在 `/swagger-ui.html`

---

> 如果未来因 monorepo 改造需要在本仓库维护前端代码,请把本文件替换为完整版(参考 rd-agent-fe 的同名文件)。
