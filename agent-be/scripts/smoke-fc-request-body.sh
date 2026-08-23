#!/usr/bin/env bash
# Smoke: FunctionCall requestBodySchema create/update/detail/publish + regression without body + OpenAPI.
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
# Detail API wraps as data.tool; create returns data as ToolVo.
need() {
  local name="$1" expr="$2" json="$3"
  if echo "$json" | python3 -c "
import sys, json
d = json.load(sys.stdin)
data = d.get('data')
tool = data.get('tool') if isinstance(data, dict) and isinstance(data.get('tool'), dict) else data
assert ($expr), 'assert failed'
" 2>/dev/null; then
    pass "$name"
  else
    fail "$name"
    echo "$json" | python3 -m json.tool 2>/dev/null | head -40 || echo "$json" | head -c 800
  fi
}

echo "== health =="
curl -sf --max-time 3 "$BASE/actuator/health" >/dev/null && pass "BE health" || fail "BE health"
curl -sf --max-time 3 -o /dev/null "$FE_BASE/" && pass "FE home" || fail "FE home"
code=$(curl -sS -o /tmp/fe_proxy.json -w '%{http_code}' --max-time 5 "${HDR[@]}" \
  "$FE_BASE/api/v1/tool/query/page?pageNo=1&pageSize=1&type=FUNCTION_CALL" || true)
if [ "$code" = "200" ]; then
  need "FE proxy tool.page" "d.get('code')==0" "$(cat /tmp/fe_proxy.json)"
else
  fail "FE proxy tool.page http=$code"
fi

TS=$(date +%s)
NAME_BODY="smoke-fc-body-${TS}"
NAME_GET="smoke-fc-get-${TS}"
NAME_OAS="smoke-fc-oas-${TS}"

echo "== MANUAL POST with requestBodySchema + responseBodySchema create/detail/update =="
CREATE_BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "$NAME_BODY",
  "description": "smoke requestBody",
  "type": "FUNCTION_CALL",
  "creationMode": "MANUAL",
  "tags": ["smoke","requestBody"],
  "baseUrl": "https://httpbin.org",
  "endpoints": [{
    "method": "POST",
    "path": "/post",
    "description": "echo json body",
    "queryParams": [{"name": "trace", "type": "STRING", "defaultValue": "1", "description": "trace", "required": True}],
    "pathParams": [],
    "headers": [{"name": "X-Smoke", "defaultValue": "1", "description": "smoke"}],
    "requestBodySchema": {
      "type": "object",
      "required": ["name"],
      "properties": {
        "name": {"type": "string", "description": "用户名"},
        "email": {"type": "string"}
      }
    },
    "requestBodyRequired": True,
    "responseBodySchema": {
      "type": "object",
      "properties": {
        "json": {"type": "object"},
        "url": {"type": "string"}
      }
    }
  }]
}, ensure_ascii=False))
PY
)

RESP=$(curl -sS "${HDR[@]}" -d "$CREATE_BODY" "$BASE/api/v1/tool/command/create")
need "create MANUAL+body code=0" "d.get('code')==0" "$RESP"
NUM_BODY=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['num'])")
echo "num_body=$NUM_BODY"

DETAIL=$(curl -sS "${HDR[@]}" "$BASE/api/v1/tool/query/detail?num=${NUM_BODY}")
need "detail has requestBodySchema.type" \
  "d.get('code')==0 and tool['endpoints'][0]['requestBodySchema']['type']=='object'" \
  "$DETAIL"
need "detail has responseBodySchema.url" \
  "'url' in tool['endpoints'][0]['responseBodySchema']['properties']" \
  "$DETAIL"
need "detail requestBodyRequired true" \
  "tool['endpoints'][0].get('requestBodyRequired') is True" \
  "$DETAIL"
need "detail query required true" \
  "tool['endpoints'][0]['queryParams'][0].get('required') is True" \
  "$DETAIL"
need "detail schema required includes name" \
  "'name' in tool['endpoints'][0]['requestBodySchema'].get('required',[])" \
  "$DETAIL"
need "detail keeps query/header" \
  "tool['endpoints'][0]['queryParams'][0]['name']=='trace' and tool['endpoints'][0]['headers'][0]['name']=='X-Smoke'" \
  "$DETAIL"

UPDATE_BODY=$(python3 - <<PY
import json
print(json.dumps({
  "num": "$NUM_BODY",
  "name": "$NAME_BODY",
  "description": "smoke requestBody updated",
  "tags": ["smoke","requestBody"],
  "baseUrl": "https://httpbin.org",
  "endpoints": [{
    "method": "POST",
    "path": "/post",
    "description": "echo json body v2",
    "queryParams": [],
    "pathParams": [],
    "headers": [],
    "requestBodySchema": {
      "type": "object",
      "properties": {
        "title": {"type": "string"},
        "count": {"type": "integer", "minimum": 0}
      },
      "required": ["title"]
    },
    "requestBodyRequired": False,
    "responseBodySchema": {
      "type": "object",
      "properties": {
        "origin": {"type": "string"},
        "json": {
          "type": "object",
          "properties": {
            "title": {"type": "string"}
          }
        }
      }
    }
  }]
}, ensure_ascii=False))
PY
)
UPD=$(curl -sS "${HDR[@]}" -d "$UPDATE_BODY" "$BASE/api/v1/tool/command/update")
need "update MANUAL+body code=0" "d.get('code')==0" "$UPD"
DETAIL2=$(curl -sS "${HDR[@]}" "$BASE/api/v1/tool/query/detail?num=${NUM_BODY}")
need "detail after update schema has title" \
  "'title' in tool['endpoints'][0]['requestBodySchema']['properties']" \
  "$DETAIL2"
need "detail after update required=false" \
  "tool['endpoints'][0].get('requestBodyRequired') in (False, None)" \
  "$DETAIL2"
need "detail after update response has origin" \
  "'origin' in tool['endpoints'][0]['responseBodySchema']['properties']" \
  "$DETAIL2"

PUB=$(curl -sS "${HDR[@]}" -d "{\"num\":\"$NUM_BODY\"}" "$BASE/api/v1/tool/command/publish")
need "publish MANUAL+body" "d.get('code')==0" "$PUB"
DETAIL_P=$(curl -sS "${HDR[@]}" "$BASE/api/v1/tool/query/detail?num=${NUM_BODY}")
need "published status + schema retained" \
  "tool['status']=='PUBLISHED' and tool['endpoints'][0]['requestBodySchema']['type']=='object'" \
  "$DETAIL_P"
need "published responseBodySchema retained" \
  "'origin' in tool['endpoints'][0]['responseBodySchema']['properties']" \
  "$DETAIL_P"

echo "== testFunctionCall POST httpbin + responseSample for fill =="
TEST_FC=$(python3 - <<PY
import json
print(json.dumps({
  "baseUrl": "https://httpbin.org",
  "endpoint": {
    "method": "POST",
    "path": "/post",
    "description": "echo",
    "queryParams": [],
    "pathParams": [],
    "headers": [{"name": "X-Smoke", "defaultValue": "1"}],
    "requestBodySchema": {
      "type": "object",
      "properties": {
        "title": {"type": "string", "default": "hello"},
        "count": {"type": "integer", "default": 1}
      }
    }
  },
  "body": {"title": "smoke-fill", "count": 2}
}, ensure_ascii=False))
PY
)
TEST_RESP=$(curl -sS "${HDR[@]}" -d "$TEST_FC" "$BASE/api/v1/tool/command/testFunctionCall")
need "testFunctionCall code=0" "d.get('code')==0" "$TEST_RESP"
need "testFunctionCall HTTP 200" \
  "d.get('data',{}).get('httpStatus')==200 and d.get('data',{}).get('success') is True" \
  "$TEST_RESP"
need "testFunctionCall responseSample is JSON object" \
  "isinstance(__import__('json').loads(d['data']['responseSample']), dict) and 'json' in __import__('json').loads(d['data']['responseSample'])" \
  "$TEST_RESP"
need "testFunctionCall echoed body title" \
  "__import__('json').loads(d['data']['responseSample']).get('json',{}).get('title')=='smoke-fill'" \
  "$TEST_RESP"

echo "== FE proxy testFunctionCall =="
FE_TEST=$(curl -sS "${HDR[@]}" -d "$TEST_FC" "$FE_BASE/api/v1/tool/command/testFunctionCall")
need "FE proxy testFunctionCall HTTP 200" \
  "d.get('code')==0 and d.get('data',{}).get('httpStatus')==200" \
  "$FE_TEST"

echo "== MANUAL GET regression (no requestBody) =="
CREATE_GET=$(python3 - <<PY
import json
print(json.dumps({
  "name": "$NAME_GET",
  "description": "smoke get only",
  "type": "FUNCTION_CALL",
  "creationMode": "MANUAL",
  "baseUrl": "https://httpbin.org",
  "endpoints": [{
    "method": "GET",
    "path": "/get",
    "description": "get echo",
    "queryParams": [{"name": "q", "type": "STRING", "description": "q"}],
    "pathParams": [],
    "headers": []
  }]
}, ensure_ascii=False))
PY
)
RESP_G=$(curl -sS "${HDR[@]}" -d "$CREATE_GET" "$BASE/api/v1/tool/command/create")
need "create MANUAL GET" "d.get('code')==0" "$RESP_G"
NUM_GET=$(echo "$RESP_G" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['num'])")
DETAIL_G=$(curl -sS "${HDR[@]}" "$BASE/api/v1/tool/query/detail?num=${NUM_GET}")
need "GET detail no requestBodySchema" \
  "d.get('code')==0 and tool['endpoints'][0].get('requestBodySchema') in (None, {})" \
  "$DETAIL_G"
need "GET detail query intact" \
  "tool['endpoints'][0]['queryParams'][0]['name']=='q'" \
  "$DETAIL_G"
PUB_G=$(curl -sS "${HDR[@]}" -d "{\"num\":\"$NUM_GET\"}" "$BASE/api/v1/tool/command/publish")
need "publish MANUAL GET" "d.get('code')==0" "$PUB_G"
UNPUB_G=$(curl -sS "${HDR[@]}" -d "{\"num\":\"$NUM_GET\"}" "$BASE/api/v1/tool/command/unpublish")
need "unpublish MANUAL GET" "d.get('code')==0" "$UNPUB_G"

echo "== OPENAPI_SPEC with requestBody (publish meta + runtime parse) =="
OAS=$(python3 - <<'PY'
import json
spec = {
  "openapi": "3.0.1",
  "info": {"title": "smoke", "version": "v1"},
  "servers": [{"url": "https://httpbin.org"}],
  "paths": {
    "/get": {"get": {"summary": "get"}},
    "/post": {
      "post": {
        "summary": "create",
        "requestBody": {
          "required": True,
          "content": {
            "application/json": {
              "schema": {"$ref": "#/components/schemas/Item"}
            }
          }
        }
      }
    }
  },
  "components": {
    "schemas": {
      "Item": {
        "type": "object",
        "required": ["name"],
        "properties": {"name": {"type": "string"}, "n": {"type": "integer"}}
      }
    }
  }
}
print(json.dumps({
  "name": "NAME_OAS_PLACEHOLDER",
  "description": "smoke openapi body",
  "type": "FUNCTION_CALL",
  "creationMode": "OPENAPI_SPEC",
  "openApiSpec": json.dumps(spec, ensure_ascii=False)
}, ensure_ascii=False))
PY
)
OAS=${OAS/NAME_OAS_PLACEHOLDER/$NAME_OAS}
RESP_O=$(curl -sS "${HDR[@]}" -d "$OAS" "$BASE/api/v1/tool/command/create")
need "create OPENAPI_SPEC" "d.get('code')==0" "$RESP_O"
NUM_OAS=$(echo "$RESP_O" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['num'])")
PUB_O=$(curl -sS "${HDR[@]}" -d "{\"num\":\"$NUM_OAS\"}" "$BASE/api/v1/tool/command/publish")
need "publish OPENAPI_SPEC" "d.get('code')==0" "$PUB_O"
DETAIL_O=$(curl -sS "${HDR[@]}" "$BASE/api/v1/tool/query/detail?num=${NUM_OAS}")
need "openapi endpointMeta count=2" \
  "tool.get('endpointMeta',{}).get('endpointCount')==2" \
  "$DETAIL_O"
need "openapi openApiSpec retained with Item \$ref" \
  "('Item' in (tool.get('openApiSpec') or '')) and ('requestBody' in (tool.get('openApiSpec') or ''))" \
  "$DETAIL_O"

echo "== page listing still works =="
PAGE=$(curl -sS "${HDR[@]}" "$BASE/api/v1/tool/query/page?pageNo=1&pageSize=50&type=FUNCTION_CALL")
need "page lists smoke tools" \
  "d.get('code')==0 and any(i.get('name','').startswith('smoke-fc-') for i in (d.get('data') or {}).get('list') or [])" \
  "$PAGE"

echo "== FE static: Body/返回参数 editor + fill helpers =="
FE_OK=1
rg -q "返回参数" /Users/jialeiwang/工作/project/agent-ops/agent-fe/src/pages/Tools/editor/FcManualForm.tsx || FE_OK=0
rg -q "responseBodySchema" /Users/jialeiwang/工作/project/agent-ops/agent-fe/src/types/tool.ts || FE_OK=0
rg -q "一键填充返回参数" /Users/jialeiwang/工作/project/agent-ops/agent-fe/src/pages/Tools/editor/FcTestConnectionModal.tsx || FE_OK=0
rg -q "inferSchemaFromJson" /Users/jialeiwang/工作/project/agent-ops/agent-fe/src/pages/Tools/editor/bodySchema.ts || FE_OK=0
rg -q "requestBodySchema" /Users/jialeiwang/工作/project/agent-ops/agent-fe/src/types/tool.ts || FE_OK=0
if [ "$FE_OK" = "1" ]; then
  pass "FE source has Body/返回参数 + 一键填充"
else
  fail "FE source missing Body/返回参数/fill helpers"
fi

echo "== cleanup =="
curl -sS "${HDR[@]}" -d "{\"num\":\"$NUM_BODY\"}" "$BASE/api/v1/tool/command/unpublish" >/dev/null || true
curl -sS "${HDR[@]}" -d "{\"num\":\"$NUM_OAS\"}" "$BASE/api/v1/tool/command/unpublish" >/dev/null || true

echo
echo "==== RESULT pass=$PASS fail=$FAIL ===="
[ "$FAIL" -eq 0 ]
