#!/usr/bin/env bash
# 用云效已推到 ACR 的镜像，滚动更新集群里的 AgentOps 前后端。
#
# 集群现状（default）：
#   agent-ops-be  → registry-vpc.cn-shenzhen.aliyuncs.com/yldfuture-qijian-pro/agent-ops-be:<tag>
#   agent-ops-fe  → registry-vpc.cn-shenzhen.aliyuncs.com/yldfuture-qijian-pro/agent-ops-fe:<tag>
#   imagePullSecrets: yldfuture
#   replicas: 2
#
# 用法：
#   # 同一 tag 同时更新 be + fe（云效常用时间戳 tag，如 2026-10-17-16-35-16）
#   ./scripts/deploy-agent-ops-images.sh 2026-10-17-16-35-16
#
#   # 只更后端 / 只更前端
#   ./scripts/deploy-agent-ops-images.sh --be 2026-10-17-16-35-16
#   ./scripts/deploy-agent-ops-images.sh --fe 2026-10-17-16-35-16
#
#   # be / fe 不同 tag
#   ./scripts/deploy-agent-ops-images.sh --be 2026-10-17-16-35-16 --fe 2026-10-17-17-00-00
#
#   # 完整镜像地址（跳过默认 registry/repo）
#   ./scripts/deploy-agent-ops-images.sh --be-image registry-vpc.cn-shenzhen.aliyuncs.com/yldfuture-qijian-pro/agent-ops-be:xxx
#
# 环境变量（可选）：
#   KUBECONFIG   默认可用本机 ~/.kube/acs-opensandbox.yaml
#   NAMESPACE    默认 default
#   REGISTRY     默认 registry-vpc.cn-shenzhen.aliyuncs.com/yldfuture-qijian-pro
#   TIMEOUT      默认 5m

set -euo pipefail

NAMESPACE="${NAMESPACE:-default}"
REGISTRY="${REGISTRY:-registry-vpc.cn-shenzhen.aliyuncs.com/yldfuture-qijian-pro}"
TIMEOUT="${TIMEOUT:-5m}"
KUBECONFIG="${KUBECONFIG:-${HOME}/.kube/acs-opensandbox.yaml}"
export KUBECONFIG

BE_TAG=""
FE_TAG=""
BE_IMAGE=""
FE_IMAGE=""
DRY_RUN=0

usage() {
  sed -n '2,35p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage 0 ;;
    --dry-run) DRY_RUN=1; shift ;;
    --be)
      BE_TAG="${2:?}"; shift 2 ;;
    --fe)
      FE_TAG="${2:?}"; shift 2 ;;
    --be-image)
      BE_IMAGE="${2:?}"; shift 2 ;;
    --fe-image)
      FE_IMAGE="${2:?}"; shift 2 ;;
    --both|"")
      shift || true ;;
    -*)
      echo "unknown flag: $1" >&2; usage 1 ;;
    *)
      # positional tag → both
      BE_TAG="${BE_TAG:-$1}"
      FE_TAG="${FE_TAG:-$1}"
      shift ;;
  esac
done

if [[ -z "$BE_TAG" && -z "$FE_TAG" && -z "$BE_IMAGE" && -z "$FE_IMAGE" ]]; then
  echo "error: 请提供镜像 tag，例如: $0 2026-10-17-16-35-16" >&2
  usage 1
fi

[[ -n "$BE_TAG" && -z "$BE_IMAGE" ]] && BE_IMAGE="${REGISTRY}/agent-ops-be:${BE_TAG}"
[[ -n "$FE_TAG" && -z "$FE_IMAGE" ]] && FE_IMAGE="${REGISTRY}/agent-ops-fe:${FE_TAG}"

echo "== kube context =="
kubectl config current-context
kubectl -n "$NAMESPACE" get deploy agent-ops-be agent-ops-fe \
  -o custom-columns='NAME:.metadata.name,IMAGE:.spec.template.spec.containers[0].image,READY:.status.readyReplicas' \
  2>/dev/null || true
echo

set_image() {
  local deploy="$1" container="$2" image="$3"
  echo "→ set ${deploy}/${container} = ${image}"
  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "  (dry-run) skip"
    return
  fi
  kubectl -n "$NAMESPACE" set image "deploy/${deploy}" "${container}=${image}"
  kubectl -n "$NAMESPACE" rollout status "deploy/${deploy}" --timeout="$TIMEOUT"
  kubectl -n "$NAMESPACE" get deploy "$deploy" \
    -o custom-columns='NAME:.metadata.name,IMAGE:.spec.template.spec.containers[0].image,READY:.status.readyReplicas'
  echo
}

[[ -n "$BE_IMAGE" ]] && set_image agent-ops-be agent-ops-be "$BE_IMAGE"
[[ -n "$FE_IMAGE" ]] && set_image agent-ops-fe agent-ops-fe "$FE_IMAGE"

echo "== done =="
kubectl -n "$NAMESPACE" get pods -l 'app in (agent-ops-be,agent-ops-fe)' -o wide
