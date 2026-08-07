# rd-agent-be OkHttp 使用现状盘点

> 分支:`feature/2026-05-26/arch-refactor`
> 盘点时间:2026-05-27
> 最后更新:2026-05-27(OkHttp 已全量退出仓库)
> 仓库:`02-项目与方案-projects/_进行中/AgentOps/代码仓库/rd-agent-be/`

---

## 一、结论(TL;DR)

**OkHttp 已从 rd-agent-be 仓库彻底移除。** 所有 HTTP 出口统一到 Hutool `cn.hutool.http.HttpRequest`。

- 0 个 Java 类 `import okhttp3.*`
- 0 处 pom.xml 声明 OkHttp 依赖(parent dependencyManagement + infra/pom.xml 均已清理)
- 0 个 yml/properties 引用 OkHttp 配置

---

## 二、清退过程

### 2.1 第一阶段 — LlmGateway 链路全删(2026-05-27 上午)
| 删除项 | 说明 |
|---|---|
| `domain/agent/gateway/LlmGateway.java` | 接口,孤儿无调用方 |
| `infra/agent/gateway/LlmGatewayImpl.java` | 实现,SSE 调公司 LLM Proxy |
| `infra/common/client/llm/` 整包 | `LlmClient + dto + param`,OkHttp + okhttp-sse 流式调用 |
| `okhttp-sse` 依赖(pom.xml 两处) | SSE 客户端,仅 LlmClient 使用 |

附带清理:`CreationMode.java` 中 "Runner 走 LlmGateway 直接调用模型" → "Runner 直接调用模型"。

### 2.2 第二阶段 — GcacClient 改用 Hutool(2026-05-27 下午)
**路径**:[rd-agent-be-infra/src/main/java/ink/garry/rd/agent/ws/infra/common/client/gcac/GcacClient.java](../代码仓库/rd-agent-be/rd-agent-be-infra/src/main/java/ink/garry/rd/agent/ws/infra/common/client/gcac/GcacClient.java)

把原本 `OkHttpClient + Request + FormBody + HttpUrl + Response` 整套替换为 Hutool `HttpRequest`:

| 场景 | 之前(OkHttp) | 之后(Hutool) |
|---|---|---|
| POST + form | `new FormBody.Builder().add(...).build()` | `HttpRequest.post(url).form(map)` |
| GET + Header | `Request.Builder().url(...).header(...).get().build()` | `HttpRequest.get(url).header(...)` |
| POST 无 body | `Request.Builder().post(EMPTY_BODY)...` | `HttpRequest.post(url).header(...)` |
| GET + query | `HttpUrl.parse(...).newBuilder().addQueryParameter(...).build()` | `HttpUtil.urlWithForm(url, map, UTF_8, false)` |
| 同步执行 | `httpClient.newCall(req).execute()` | `request.execute()` |
| 超时注入 | `OkHttpClient.Builder().connectTimeout(...).readTimeout(...).build()` | `request.setConnectionTimeout(...).setReadTimeout(...)` |
| IO 异常 | `IOException` | `HttpException` |
| 状态码判断 | `resp.isSuccessful()` | `resp.isOk()` |

公开 API(`exchangeToken / fetchProfile / verifyPersonalToken / generatePersonalToken / listPersonalTokens / revokePersonalToken`)签名保持不变。

### 2.3 第三阶段 — Mock + GcacGateway 链路全删(2026-05-27 晚)
基于"dev 与 test 一致就好"的决策,Mock 链路整体撤除,且 facade 不再需要 `GcacGateway` 抽象(应用层直接用 infra 的 `GcacClient`)。

| 删除项 | 原职责 | 撤除原因 |
|---|---|---|
| `facade/auth/gcac/GcacGateway.java` | OAuth2 网关接口 | application 直接用 `GcacClient`,接口冗余 |
| `infra/auth/gcac/GcacGatewayImpl.java` | 接口实现,与 GcacClient 功能重叠且混用 OkHttp + Hutool | 与 `GcacClient` 重复 |
| `infra/auth/gcac/MockGcacGatewayImpl.java` | dev profile + `gcac.client-id=mock-client-id` 时的本地 Mock | dev 直接走 GCAC test 真实环境 |
| `adapter/auth/MockGcacController.java` | Mock 的 `/mock-gcac/authorize` 选用户页 | 同上 |

`application/auth/GcacLoginService.java` 改造:
- 注入字段: `private final GcacGateway gcacGateway;` → `private final GcacClient gcacClient;`
- 调用点改用 Param builder:
  ```java
  GcacTokenDTO token = gcacClient.exchangeToken(GcacTokenExchangeParam.builder()
          .code(code)
          .redirectUri(gcacProps.getRedirectUri())
          .build());
  GcacProfileDTO profile = gcacClient.fetchProfile(GcacProfileFetchParam.builder()
          .tokenType(token.getTokenType())
          .accessToken(token.getAccessToken())
          .build());
  ```

### 2.4 第四阶段 — OkHttp 依赖彻底从 pom 删除(2026-05-27 晚)
代码层无 import 后,pom 依赖也无意义,一并清除:
- `pom.xml`:删除 `<okhttp.version>4.12.0</okhttp.version>` 属性 + dependencyManagement 中的 OkHttp 依赖块
- `rd-agent-be-infra/pom.xml`:删除 OkHttp `<dependency>` 块
- JavaDoc 残留清理:
  - `GcacProperties.java`:"OkHttp 连接/读超时" → "HTTP 连接/读超时"
  - `SkillSyncCommandService.java`:"OkHttp 调外部公司库" → "HTTP 客户端调外部公司库"

---

## 三、当前 HTTP 出口统计

| 类 | 位置 | 用途 | HTTP 客户端 |
|---|---|---|---|
| `GcacClient` | `infra/common/client/gcac/` | GCAC OAuth2(token / profile)+ self-service(verify)+ IAM Selfserv 个人 token(generate / list / revoke) | Hutool `HttpRequest` |

仓库内已无其他生产 HTTP 出口。`SkillSyncCommandService` 提到的 `SkillRepoGateway` 尚未实现(占位骨架)。

---

## 四、变更历史

| 日期 | 操作 |
|---|---|
| 2026-05-27 | 删除 `LlmGateway` / `LlmGatewayImpl` 整条 `LlmClient + dto + param` 链路;同步移除 `okhttp-sse` 依赖与 `CreationMode` JavaDoc 引用 |
| 2026-05-27 | `GcacClient` 由 OkHttp 改为 Hutool `HttpRequest` 实现 |
| 2026-05-27 | 删除 `GcacGateway` 接口 + `GcacGatewayImpl` + `MockGcacGatewayImpl` + `MockGcacController`;`GcacLoginService` 直接注入 `GcacClient` |
| 2026-05-27 | 删除 parent pom `okhttp.version` 属性 + `okhttp` 依赖管理;删除 infra/pom.xml 的 OkHttp 引用;清理 `GcacProperties` / `SkillSyncCommandService` 的 OkHttp JavaDoc 残留 |

---

## 五、附:验证命令

```bash
cd "代码仓库/rd-agent-be"

# 1. 验证 0 处 OkHttp 引用
grep -rn -E "okhttp3|OkHttp" \
  --include="*.java" --include="*.xml" \
  --include="*.yml" --include="*.yaml" --include="*.properties"

# 2. 编译验证
mvn -q -DskipTests compile
```

预期:第 1 条 grep 无输出;第 2 条编译成功。
