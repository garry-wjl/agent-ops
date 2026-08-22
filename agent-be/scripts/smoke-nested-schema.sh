#!/usr/bin/env bash
# 评测集 Schema 层级展开：新增行 / 模板列 / 导入反展平 / 导出扁平列
set -euo pipefail
BASE="${BASE:-http://127.0.0.1:8081}"
USER_ID="${USER_ID:-alice.zhang}"
WS="${WS:-WS-1997fd034709}"
AUTH=(-H "X-User-Id: ${USER_ID}" -H "X-Workspace-Num: ${WS}")
JSON_HDR=("${AUTH[@]}" -H "Content-Type: application/json")
PASS=0
FAIL=0
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
TS=$(date +%s)

pass() { echo "PASS  $1"; PASS=$((PASS+1)); }
fail() { echo "FAIL  $1"; FAIL=$((FAIL+1)); }

assert_code() {
  local name="$1" body="$2" expect="${3:-0}"
  local code
  code=$(printf '%s' "$body" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("code"))')
  if [[ "$code" == "$expect" ]]; then
    pass "$name"
  else
    echo "FAIL  $name  code=$code body=$(printf '%s' "$body" | head -c 400)"
    FAIL=$((FAIL+1))
  fi
}

python3 - <<'PY' > "$TMP/schema.json"
import json
print(json.dumps([
  {"name":"input","type":"string"},
  {"name":"reference","type":"string"},
  {"name":"context","type":"object","properties":{
    "orderId":{"type":"string"},
    "tags":{"type":"array","items":{"type":"string"}},
    "profile":{"type":"object","properties":{"city":{"type":"string"}}}
  }},
  {"name":"items","type":"array","items":{"type":"object","properties":{
    "sku":{"type":"string"},
    "qty":{"type":"string"}
  }}}
], ensure_ascii=False))
PY

echo "=== N1 create nested-schema dataset ==="
BODY=$(python3 - <<PY
import json
schema=open("$TMP/schema.json").read()
print(json.dumps({
  "name": "nested-schema-$TS",
  "type": "CUSTOM",
  "schemaJson": schema
}, ensure_ascii=False))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/dataset/command/create")
assert_code "nested.create" "$R"
DS=$(printf '%s' "$R" | python3 -c 'import json,sys; d=json.load(sys.stdin).get("data") or {}; print(d.get("num",""))')
[[ -n "$DS" ]] || { echo "ABORT no dataset"; exit 1; }
echo "  dataset=$DS"

echo "=== N2 addRow nested data ==="
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "datasetNum": "$DS",
  "data": {
    "input": "你好",
    "reference": "好的",
    "context": {
      "orderId": "ORD-9",
      "tags": ["a", "b"],
      "profile": {"city": "上海"}
    },
    "items": [{"sku": "S1", "qty": "2"}]
  }
}, ensure_ascii=False))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/dataset/command/addRow")
assert_code "nested.addRow" "$R"

echo "=== N3 rows contain nested JSON ==="
R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/dataset/query/rows?num=$DS")
assert_code "nested.rows" "$R"
if printf '%s' "$R" | python3 -c '
import json,sys
rows=json.load(sys.stdin).get("data") or []
ok=False
for row in rows:
  d=json.loads(row.get("dataJson") or "{}")
  if (d.get("context",{}) or {}).get("orderId")=="ORD-9" \
     and (d.get("context",{}) or {}).get("profile",{}).get("city")=="上海" \
     and "a" in ((d.get("context",{}) or {}).get("tags") or []) \
     and (d.get("items") or [{}])[0].get("sku")=="S1":
    ok=True
sys.exit(0 if ok else 1)
'; then
  pass "nested.rows.structure"
else
  fail "nested.rows.structure"
fi

echo "=== N4 template headers flattened ==="
HTTP=$(curl -sS -o "$TMP/tpl.xlsx" -w "%{http_code}" "${AUTH[@]}" \
  "$BASE/api/v1/evaluation/dataset/query/template?num=$DS&type=CUSTOM")
if [[ "$HTTP" == "200" ]]; then pass "nested.template.http"; else fail "nested.template.http=$HTTP"; fi
if python3 - <<PY
from openpyxl import load_workbook
wb=load_workbook("$TMP/tpl.xlsx")
ws=wb.active
headers=[c.value for c in next(ws.iter_rows(min_row=1,max_row=1))]
need=["input","reference","context.orderId","context.tags[0]","context.profile.city","items[0].sku"]
missing=[h for h in need if h not in headers]
if missing:
    print("missing=", missing, "got=", headers)
    raise SystemExit(1)
print("headers ok")
PY
then
  pass "nested.template.headers"
else
  fail "nested.template.headers"
fi

echo "=== N5 import flattened xlsx ==="
python3 - <<PY
from openpyxl import Workbook
wb=Workbook(); ws=wb.active
ws.append(["input","reference","context.orderId","context.tags[0]","context.tags[1]","context.profile.city","items[0].sku","items[0].qty"])
ws.append(["导入行","参考","ORD-IMP","t1","t2","北京","SKU-IMP","3"])
wb.save("$TMP/imp.xlsx")
PY
R=$(curl -sS "${AUTH[@]}" -F "num=$DS" -F "file=@$TMP/imp.xlsx" \
  "$BASE/api/v1/evaluation/dataset/command/importXlsx")
assert_code "nested.importXlsx" "$R"

R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/dataset/query/rows?num=$DS")
assert_code "nested.rows.afterImport" "$R"
if printf '%s' "$R" | python3 -c '
import json,sys
rows=json.load(sys.stdin).get("data") or []
ok=False
for row in rows:
  d=json.loads(row.get("dataJson") or "{}")
  if d.get("input")=="导入行" \
     and (d.get("context",{}) or {}).get("orderId")=="ORD-IMP" \
     and (d.get("context",{}) or {}).get("profile",{}).get("city")=="北京" \
     and (d.get("items") or [{}])[0].get("sku")=="SKU-IMP":
    ok=True
sys.exit(0 if ok else 1)
'; then
  pass "nested.import.unflatten"
else
  fail "nested.import.unflatten"
  echo "  body=$(printf '%s' "$R" | head -c 600)"
fi

echo "=== N6 publish + export flattened ==="
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"num\":\"$DS\"}" "$BASE/api/v1/evaluation/dataset/command/publish")
assert_code "nested.publish" "$R"
VER=$(printf '%s' "$R" | python3 -c 'import json,sys; d=json.load(sys.stdin).get("data") or {}; print(d.get("version",""))')
HTTP=$(curl -sS -o "$TMP/exp.xlsx" -w "%{http_code}" "${AUTH[@]}" \
  "$BASE/api/v1/evaluation/dataset/query/exportXlsx?num=$DS&version=$VER")
if [[ "$HTTP" == "200" ]]; then pass "nested.export.http"; else fail "nested.export.http=$HTTP"; fi
if python3 - <<PY
from openpyxl import load_workbook
wb=load_workbook("$TMP/exp.xlsx")
ws=wb.active
headers=[c.value for c in next(ws.iter_rows(min_row=1,max_row=1))]
rows=list(ws.iter_rows(min_row=2, values_only=True))
idx={h:i for i,h in enumerate(headers)}
if "context.orderId" not in headers:
    print("headers=", headers)
    raise SystemExit(1)
ok=False
for r in rows:
  if not r: continue
  def cell(h):
    i=idx.get(h)
    return r[i] if i is not None and i < len(r) else None
  if cell("input")=="导入行" and cell("context.orderId")=="ORD-IMP" and cell("context.profile.city")=="北京" and cell("items[0].sku")=="SKU-IMP":
    ok=True
if not ok:
    print("headers=", headers)
    print("rows=", rows[:5])
    raise SystemExit(1)
PY
then
  pass "nested.export.flatValues"
else
  fail "nested.export.flatValues"
fi

echo "=== N7 cleanup ==="
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"num\":\"$DS\"}" "$BASE/api/v1/evaluation/dataset/command/delete")
assert_code "nested.delete" "$R"

echo ""
echo "NESTED RESULT: PASS=$PASS FAIL=$FAIL"
[[ "$FAIL" -eq 0 ]]
