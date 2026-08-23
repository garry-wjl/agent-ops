#!/usr/bin/env bash
# Smoke: Agent 工具双模式绑定 — mountableItems + 整组/具体 toolRefs 持久化。
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:8081}"
FE_BASE="${FE_BASE:-http://127.0.0.1:8001}"
WS="${WS:-WS-1997fd034709}"
USER_ID="${USER_ID:-alice.zhang}"
HDR=(-H "Content-Type: application/json" -H "X-User-Id: ${USER_ID}" -H "X-Workspace-Num: ${WS}")

PASS=0
FAIL=0
fail() { echo "FAIL  $*"; FAIL=$((FAIL+1)); }
pass() { echo "PASS  $*"; PASS=$((PASS+1)); }
need() {
  local name="$1" expr="$2" json="$3"
  if echo "$json" | python3 -c "
import sys, json
d = json.load(sys.stdin)
assert ($expr), 'assert failed'
" 2>/dev/null; then
    pass "$name"
  else
    fail "$name"
    echo "$json" | python3 -m json.tool 2>/dev/null | head -50 || echo "$json" | head -c 800
  fi
}

echo "== health =="
curl -sf --max-time 3 "$BASE/actuator/health" >/dev/null && pass "BE health" || fail "BE health"
curl -sf --max-time 3 -o /dev/null "$FE_BASE/" && pass "FE home" || fail "FE home"

echo "== mountableItems =="
ITEMS_JSON=$(curl -sS --max-time 60 "${HDR[@]}" "$BASE/api/v1/tool/query/mountableItems")
need "mountableItems code=0" "d.get('code')==0 and isinstance(d.get('data'), list)" "$ITEMS_JSON"
need "mountableItems has FC bindingKey" \
  "any(isinstance(i,dict) and i.get('itemKind')=='FC_ENDPOINT' and '|' in (i.get('bindingKey') or '') for i in (d.get('data') or []))" \
  "$ITEMS_JSON"

GROUPS_JSON=$(curl -sS --max-time 15 "${HDR[@]}" "$BASE/api/v1/tool/query/mountable")
need "mountable groups code=0" "d.get('code')==0 and isinstance(d.get('data'), list) and len(d.get('data'))>=1" "$GROUPS_JSON"

eval "$(echo "$ITEMS_JSON" | python3 -c '
import json,sys,shlex
d=json.load(sys.stdin)
items=d.get("data") or []
fc=[i for i in items if i.get("itemKind")=="FC_ENDPOINT"]
mcp=[i for i in items if i.get("itemKind")=="MCP_TOOL"]
fc0=fc[0] if fc else {}
# prefer a different parent for whole-group if available
parents=sorted({i.get("toolNum") for i in items if i.get("toolNum")})
group_other=next((p for p in parents if p and p!=fc0.get("toolNum")), "")
print("FC_NUM="+shlex.quote(fc0.get("toolNum") or ""))
print("FC_METHOD="+shlex.quote(fc0.get("method") or "GET"))
print("FC_PATH="+shlex.quote(fc0.get("path") or ""))
print("GROUP_OTHER="+shlex.quote(group_other))
print("HAS_FC="+("1" if fc else "0"))
')"

if [ "$HAS_FC" != "1" ]; then
  fail "no FC endpoints in mountableItems — cannot continue agent bind cases"
  echo "SUMMARY pass=$PASS fail=$FAIL"
  exit 1
fi

# resolve modelId
MODEL_ID=$(curl -sS "${HDR[@]}" "$BASE/api/v1/models/selectable" | python3 -c '
import json,sys
d=json.load(sys.stdin)
xs=d.get("data") or []
if isinstance(xs, dict):
  xs=xs.get("list") or []
print(xs[0].get("num") if xs else "")
')
if [ -z "$MODEL_ID" ]; then
  # fallback: from existing agent snapshot
  MODEL_ID=$(curl -sS "${HDR[@]}" "$BASE/api/v1/agents/page?pageNo=1&pageSize=20" | python3 -c '
import json,sys
d=json.load(sys.stdin)
nums=[a.get("num") for a in ((d.get("data") or {}).get("list") or []) if a.get("creationMode")=="CONFIG"]
print(nums[0] if nums else "")
')
  if [ -n "$MODEL_ID" ]; then
    AGENT_FOR_MODEL="$MODEL_ID"
    MODEL_ID=$(curl -sS "${HDR[@]}" "$BASE/api/v1/agents/detail?agentNum=$AGENT_FOR_MODEL" | python3 -c '
import json,sys
d=json.load(sys.stdin)
cs=((d.get("data") or {}).get("currentVersion") or {}).get("configSnapshot") or {}
print(cs.get("modelId") or "")
')
  fi
fi
need "has modelId for agent create" "True" "{\"code\":0,\"data\":$(python3 -c "import json;print(json.dumps(bool('$MODEL_ID')))")}"
if [ -z "$MODEL_ID" ]; then
  fail "cannot resolve modelId"
  echo "SUMMARY pass=$PASS fail=$FAIL"
  exit 1
fi
pass "modelId=$MODEL_ID"

TS=$(date +%s)
NAME="smoke-toolbind-${TS}"

echo "== create Agent with concrete FC + optional whole-group other =="
CREATE_BODY=$(python3 - <<PY
import json
refs=[{
  "toolNum": "$FC_NUM",
  "itemKind": "FC_ENDPOINT",
  "method": "$FC_METHOD",
  "path": "$FC_PATH",
}]
nums=["$FC_NUM"]
other="$GROUP_OTHER"
if other and other != "$FC_NUM":
  refs.append({"toolNum": other})
  nums.append(other)
print(json.dumps({
  "name": "$NAME",
  "description": "smoke dual-mode tool bind",
  "agentType": "NORMAL",
  "systemPrompt": "you are smoke agent for tool binding",
  "modelId": "$MODEL_ID",
  "temperature": 0.2,
  "enablePlan": False,
  "maxIters": 5,
  "skillNums": [],
  "skillRefs": [],
  "toolNums": nums,
  "toolRefs": refs,
  "memoryConfig": {"shortTermStrategy": "NONE", "longTermStrategy": "NONE"},
}))
PY
)

CREATE_JSON=$(curl -sS "${HDR[@]}" -d "$CREATE_BODY" "$BASE/api/v1/agents/create")
need "agent create code=0" "d.get('code')==0 and bool((d.get('data') or {}).get('agentNum'))" "$CREATE_JSON"
AGENT_NUM=$(echo "$CREATE_JSON" | python3 -c "import json,sys;d=json.load(sys.stdin);print((d.get('data') or {}).get('agentNum') or '')")

DETAIL_JSON=$(curl -sS "${HDR[@]}" "$BASE/api/v1/agents/detail?agentNum=$AGENT_NUM")
need "detail echoes concrete toolRef" \
  "any(r.get('itemKind')=='FC_ENDPOINT' and r.get('toolNum')=='$FC_NUM' and (r.get('path') or '')=='$FC_PATH' for r in ((((d.get('data') or {}).get('currentVersion') or {}).get('configSnapshot') or {}).get('toolRefs') or []))" \
  "$DETAIL_JSON"

if [ -n "$GROUP_OTHER" ] && [ "$GROUP_OTHER" != "$FC_NUM" ]; then
  need "detail echoes whole-group ref coexist" \
    "any((not r.get('itemKind')) and r.get('toolNum')=='$GROUP_OTHER' for r in ((((d.get('data') or {}).get('currentVersion') or {}).get('configSnapshot') or {}).get('toolRefs') or []))" \
    "$DETAIL_JSON"
fi

echo "== create Agent legacy whole-group only (toolNums) =="
LEGACY_NAME="smoke-toolbind-legacy-${TS}"
LEGACY_BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "$LEGACY_NAME",
  "description": "smoke legacy whole group",
  "agentType": "NORMAL",
  "systemPrompt": "legacy whole group bind",
  "modelId": "$MODEL_ID",
  "temperature": 0.2,
  "enablePlan": False,
  "maxIters": 5,
  "skillNums": [],
  "toolNums": ["$FC_NUM"],
  "memoryConfig": {"shortTermStrategy": "NONE", "longTermStrategy": "NONE"},
}))
PY
)
LEGACY_JSON=$(curl -sS "${HDR[@]}" -d "$LEGACY_BODY" "$BASE/api/v1/agents/create")
need "legacy create code=0" "d.get('code')==0" "$LEGACY_JSON"
LEGACY_NUM=$(echo "$LEGACY_JSON" | python3 -c "import json,sys;d=json.load(sys.stdin);print((d.get('data') or {}).get('agentNum') or '')")
LEGACY_DETAIL=$(curl -sS "${HDR[@]}" "$BASE/api/v1/agents/detail?agentNum=$LEGACY_NUM")
need "legacy detail has toolNums or whole-group toolRefs" \
  "('$FC_NUM' in ((((d.get('data') or {}).get('currentVersion') or {}).get('configSnapshot') or {}).get('toolNums') or [])) or any((not r.get('itemKind')) and r.get('toolNum')=='$FC_NUM' for r in ((((d.get('data') or {}).get('currentVersion') or {}).get('configSnapshot') or {}).get('toolRefs') or []))" \
  "$LEGACY_DETAIL"

echo "== FE proxy mountableItems =="
FE_ITEMS=$(curl -sS --max-time 60 "${HDR[@]}" "$FE_BASE/api/v1/tool/query/mountableItems" || true)
need "FE proxy mountableItems" "d.get('code')==0 and isinstance(d.get('data'), list)" "$FE_ITEMS"

echo "SUMMARY pass=$PASS fail=$FAIL"
if [ "$FAIL" -gt 0 ]; then exit 1; fi
