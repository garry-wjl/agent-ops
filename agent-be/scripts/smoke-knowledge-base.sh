#!/usr/bin/env bash
# Smoke: 知识库管理全接口 — 技术方案 §7.2 + 工作空间 KB 配置。
# 用法: ./scripts/smoke-knowledge-base.sh
# 环境变量: BASE WS USER_ID MODEL_ID MYSQL_CONTAINER
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:8081}"
USER_ID="${USER_ID:-alice.zhang}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql-test}"
TS=$(date +%s)
KB_NAME="smoke-kb-${TS}"
OSS_FILE_ID="smoke-kb-${TS}.txt"

PASS=0
FAIL=0
fail() { echo "FAIL  $*"; FAIL=$((FAIL + 1)); }
pass() { echo "PASS  $*"; PASS=$((PASS + 1)); }

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
    echo "$json" | python3 -m json.tool 2>/dev/null | head -40 || echo "$json" | head -c 800
  fi
}

post() {
  curl -sS --max-time 60 "${HDR[@]}" -X POST "$BASE$1" -d "$2"
}

get() {
  curl -sS --max-time 60 "${HDR[@]}" "$BASE$1"
}

echo "== health =="
curl -sf --max-time 3 "$BASE/actuator/health" >/dev/null && pass "BE health" || fail "BE health"

echo "== resolve workspace =="
if [ -z "${WS:-}" ]; then
  WS=$(curl -sS -H "X-User-Id: ${USER_ID}" "$BASE/api/v1/workspace/list" | python3 -c '
import json,sys
d=json.load(sys.stdin)
xs=d.get("data") or []
print(xs[0].get("num") if xs else "")
')
fi
need "workspace resolved" "True" "{\"code\":0,\"data\":$(python3 -c "import json;print(json.dumps(bool('${WS}')))")}"
HDR=(-H "Content-Type: application/json" -H "X-User-Id: ${USER_ID}" -H "X-Workspace-Num: ${WS}")
pass "WS=${WS}"

echo "== resolve embedding modelId =="
if [ -z "${MODEL_ID:-}" ]; then
  MODEL_ID=$(get "/api/v1/models/selectable" | python3 -c '
import json,sys
d=json.load(sys.stdin)
xs=d.get("data") or []
print(xs[0].get("num") if xs else "")
')
fi
need "modelId resolved" "True" "{\"code\":0,\"data\":$(python3 -c "import json;print(json.dumps(bool('${MODEL_ID}')))")}"
pass "MODEL_ID=${MODEL_ID}"

echo "== workspace kbConfig GET/POST =="
CFG_GET=$(get "/api/v1/workspace/query/kbConfig")
need "kbConfig get code=0" "d.get('code')==0" "$CFG_GET"
CFG_SAVE=$(post "/api/v1/workspace/command/saveKbConfig" "$(python3 - <<PY
import json
print(json.dumps({
  "allowedKbTypes": ["SIMPLE", "RAG_FLOW"],
  "defaultEmbeddingModelId": "${MODEL_ID}",
  "defaultChunkSize": 512,
  "defaultChunkOverlap": 64,
  "defaultSplitStrategy": "PARAGRAPH",
  "maxFileSizeMb": 50
}))
PY
)")
need "kbConfig save code=0" "d.get('code')==0" "$CFG_SAVE"

echo "== query: typeSchemas / list (before create) =="
SCHEMAS=$(get "/api/v1/knowledge-base/query/typeSchemas")
need "typeSchemas code=0" "d.get('code')==0 and isinstance(d.get('data'), list)" "$SCHEMAS"
LIST0=$(get "/api/v1/knowledge-base/query/list?pageNo=1&pageSize=5")
need "list code=0" "d.get('code')==0 and isinstance((d.get('data') or {}).get('list'), list)" "$LIST0"
MOUNT0=$(get "/api/v1/knowledge-base/query/mountable")
need "mountable code=0" "d.get('code')==0 and isinstance(d.get('data'), list)" "$MOUNT0"

echo "== command: create =="
CREATE_BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "${KB_NAME}",
  "description": "smoke kb ${TS}",
  "kbType": "SIMPLE",
  "indexConfig": {
    "embeddingModelId": "${MODEL_ID}",
    "splitStrategy": "PARAGRAPH",
    "chunkSize": 512,
    "chunkOverlap": 64
  },
  "retrievalDefaults": {"topK": 5, "minScore": 0.5}
}))
PY
)
CREATE=$(post "/api/v1/knowledge-base/command/create" "$CREATE_BODY")
need "create code=0" "d.get('code')==0 and (d.get('data') or {}).get('kbNum')" "$CREATE"
KB_NUM=$(echo "$CREATE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["kbNum"])')
pass "KB_NUM=${KB_NUM}"

echo "== query: detail / configAlignment / list / mountable =="
DETAIL=$(get "/api/v1/knowledge-base/query/detail?kbNum=${KB_NUM}")
need "detail code=0" "d.get('code')==0 and (d.get('data') or {}).get('kbNum')=='${KB_NUM}'" "$DETAIL"
ALIGN=$(get "/api/v1/knowledge-base/query/configAlignment?kbNum=${KB_NUM}")
need "configAlignment code=0" "d.get('code')==0 and isinstance(d.get('data'), list)" "$ALIGN"
LIST1=$(get "/api/v1/knowledge-base/query/list?pageNo=1&pageSize=20&keyword=${KB_NAME}")
need "list contains created kb" \
  "any((x or {}).get('kbNum')=='${KB_NUM}' for x in ((d.get('data') or {}).get('list') or []))" \
  "$LIST1"
MOUNT1=$(get "/api/v1/knowledge-base/query/mountable")
need "mountable contains created kb" \
  "any((x or {}).get('kbNum')=='${KB_NUM}' for x in (d.get('data') or []))" \
  "$MOUNT1"

echo "== command: updateBasic / updateIndexConfig =="
UPD_BASIC=$(post "/api/v1/knowledge-base/command/updateBasic" "$(python3 - <<PY
import json
print(json.dumps({"kbNum":"${KB_NUM}","name":"${KB_NAME}-v2","description":"updated"}))
PY
)")
need "updateBasic code=0" "d.get('code')==0" "$UPD_BASIC"
UPD_CFG=$(post "/api/v1/knowledge-base/command/updateIndexConfig" "$(python3 - <<PY
import json
print(json.dumps({
  "kbNum":"${KB_NUM}",
  "indexConfig":{"embeddingModelId":"${MODEL_ID}","splitStrategy":"PARAGRAPH","chunkSize":600,"chunkOverlap":80}
}))
PY
)")
need "updateIndexConfig code=0" "d.get('code')==0" "$UPD_CFG"

echo "== seed chat_attachment for registerFile =="
docker exec "$MYSQL_CONTAINER" mysql -uroot -p123456 rd_agent -e "
INSERT INTO chat_attachment (num, workspace_num, file_id, file_name, mime_type, size_bytes, kind, create_no, update_no)
VALUES ('CHA-smoke-${TS}', '${WS}', '${OSS_FILE_ID}', 'smoke.txt', 'text/plain', 128, 'FILE', '${USER_ID}', '${USER_ID}')
ON DUPLICATE KEY UPDATE workspace_num='${WS}';
" >/dev/null 2>&1 && pass "seed chat_attachment" || fail "seed chat_attachment"

echo "== command: registerFile / files / reindex* =="
REG=$(post "/api/v1/knowledge-base/command/registerFile" "$(python3 - <<PY
import json
print(json.dumps({
  "kbNum":"${KB_NUM}",
  "ossFileId":"${OSS_FILE_ID}",
  "fileName":"smoke.txt",
  "mimeType":"text/plain",
  "fileSize":128
}))
PY
)")
need "registerFile code=0" "d.get('code')==0" "$REG"
FILES=$(get "/api/v1/knowledge-base/query/files?kbNum=${KB_NUM}")
need "files code=0" "d.get('code')==0 and len(d.get('data') or [])>=1" "$FILES"
FILE_NUM=$(echo "$FILES" | python3 -c 'import json,sys; xs=json.load(sys.stdin).get("data") or []; print(xs[0].get("fileNum") if xs else "")')
need "fileNum resolved" "True" "{\"code\":0,\"data\":$(python3 -c "import json;print(json.dumps(bool('${FILE_NUM}')))")}"

REINDEX_FILE=$(post "/api/v1/knowledge-base/command/reindexFile" "{\"kbNum\":\"${KB_NUM}\",\"fileNum\":\"${FILE_NUM}\"}")
need "reindexFile code=0" "d.get('code')==0" "$REINDEX_FILE"
REINDEX_STALE=$(post "/api/v1/knowledge-base/command/reindexStaleFiles" "{\"kbNum\":\"${KB_NUM}\"}")
need "reindexStaleFiles code=0" "d.get('code')==0" "$REINDEX_STALE"
REINDEX_ALL=$(post "/api/v1/knowledge-base/command/reindexAll" "{\"kbNum\":\"${KB_NUM}\"}")
need "reindexAll code=0" "d.get('code')==0" "$REINDEX_ALL"

echo "== query: testRetrieve =="
TEST_RET=$(post "/api/v1/knowledge-base/query/testRetrieve" "$(python3 - <<PY
import json
print(json.dumps({"kbNum":"${KB_NUM}","question":"smoke test","topK":3,"minScore":0.1}))
PY
)")
need "testRetrieve code=0" "d.get('code')==0 and isinstance(d.get('data'), list)" "$TEST_RET"

echo "== command: deleteFile / delete =="
DEL_FILE=$(post "/api/v1/knowledge-base/command/deleteFile" "{\"kbNum\":\"${KB_NUM}\",\"fileNum\":\"${FILE_NUM}\"}")
need "deleteFile code=0" "d.get('code')==0" "$DEL_FILE"
DEL_KB=$(post "/api/v1/knowledge-base/command/delete" "{\"kbNum\":\"${KB_NUM}\"}")
need "delete code=0" "d.get('code')==0" "$DEL_KB"

echo ""
echo "SUMMARY pass=$PASS fail=$FAIL"
if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
