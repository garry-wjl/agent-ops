# ACS Agent Sandbox 接入（去掉会话级 OSS）

## 背景

- ACS 不支持 OpenSandbox Fast Sandbox（Firecracker/KVM）。
- 改用 ACS 产品能力：**Agent Sandbox**（`compute-class: agent-sandbox`）+ `SandboxSet` 预热池 + E2B 协议（`ack-sandbox-manager`）。
- 会话级 OSS `subPath` 与预热池冲突，且多数场景不需要跨会话磁盘隔离 → **下线**。

## 集群侧（已建）

| 项 | 值 |
| --- | --- |
| 命名空间 | `sandbox-system`（控制面）、`agent-sandbox`（预热池） |
| 组件 | OpenKruise `agents-sandbox-controller` / `agents-sandbox-manager` v0.6.0 |
| 预热池 | `SandboxSet/code-interpreter`：0.5C / 1Gi × 3 |
| 镜像 | `registry-cn-shenzhen-vpc.ack.aliyuncs.com/acs/code-interpreter:v1.6` |
| E2B domain | `agent-sandbox.yldfuture.com`（需 DNS `*` / `api` → ALB） |
| IngressClass | `qijian-ai-prod-alb-ingress-class`（仅 HTTPS:443） |

冒烟：E2B `POST /sandboxes` 领取预热实例；envd 写文件 + `Process/Start` 执行命令；同实例内数据保留。

## 应用侧

- `sandbox.provider=acs`：走 `AcsE2bSandboxClient` + `AcsAgentSandboxContainerGateway` + `AcsAgentSandboxExecBridge`。
- `sandbox.oss.enabled` 固定 false；`isolatesWorkspaceBySession()` 恒 false。
- 会话绑定逻辑不变：同 `sessionNum` 复用同一 `sandboxId` → 会话期间磁盘不丢；实例死亡则 rebind + 重投 skill。

### 关键

```yaml
sandbox:
  provider: acs
  api-key: ${SANDBOX_API_KEY}
  domain: agent-sandbox.yldfuture.com
  protocol: https
  template: code-interpreter
  # 本地联调 port-forward 时：
  # manager-endpoint: http://127.0.0.1:18080
  # gateway-endpoint: http://127.0.0.1:17788
```

### 运维待办

1. DNS：`agent-sandbox.yldfuture.com` / `*.agent-sandbox.yldfuture.com` / `api.agent-sandbox.yldfuture.com` → ALB。
2. Ingress TLS Secret `sandbox-manager-tls`（sandbox-system）。
3. 生产 API Key 用普通 key（勿长期用 admin key）。
