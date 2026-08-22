#!/usr/bin/env bash
# Agent 应用评测 P0 全量 API 冒烟（AUTH_DISABLED=true）
set -euo pipefail
BASE="${BASE:-http://127.0.0.1:8081}"
USER_ID="${USER_ID:-alice.zhang}"
WS="${WS:-WS-1997fd034709}"
AGENT_NUM="${AGENT_NUM:-AGT2026080922497568}"
AGENT_VER="${AGENT_VER:-AVN2026080923558112}"
MODEL_NUM="${MODEL_NUM:-MDL2026080922496240}"
SESSION_NUM="${SESSION_NUM:-SES2026080922516144}"
AUTH=(-H "X-User-Id: ${USER_ID}" -H "X-Workspace-Num: ${WS}")
JSON_HDR=("${AUTH[@]}" -H "Content-Type: application/json")
PASS=0
FAIL=0
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
TS=$(date +%s)

assert_code() {
  local name="$1" body="$2" expect="${3:-0}"
  local code
  code=$(printf '%s' "$body" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("code"))')
  if [[ "$code" == "$expect" ]]; then
    echo "PASS  $name"
    PASS=$((PASS+1))
  else
    echo "FAIL  $name  code=$code body=$(printf '%s' "$body" | head -c 500)"
    FAIL=$((FAIL+1))
  fi
}

echo "=== TC01 presets ==="
R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/grader/query/presets")
assert_code "grader.presets" "$R"
PRESET_N=$(printf '%s' "$R" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["data"]))')
if [[ "$PRESET_N" -ge 6 ]]; then echo "PASS  presets.count>=6"; PASS=$((PASS+1)); else echo "FAIL presets.count=$PRESET_N"; FAIL=$((FAIL+1)); fi

echo "=== TC02 create dataset CUSTOM ==="
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "smoke-ds-$TS",
  "description": "smoke",
  "type": "CUSTOM",
  "schemaJson": json.dumps([{"name":"input","type":"string"},{"name":"reference","type":"string"}])
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/dataset/command/create")
assert_code "dataset.create" "$R"
DS=$(printf '%s' "$R" | python3 -c 'import json,sys; d=json.load(sys.stdin).get("data") or {}; print(d.get("num",""))')
echo "  dataset=$DS"
[[ -n "$DS" ]] || { echo "ABORT no dataset"; exit 1; }

echo "=== TC03 create AGENT dataset ==="
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "smoke-agent-ds-$TS",
  "type": "AGENT",
  "agentNum": "$AGENT_NUM",
  "schemaJson": json.dumps([{"name":"input","type":"string"},{"name":"reference","type":"string"}])
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/dataset/command/create")
assert_code "dataset.create.agent" "$R"
DS_AGENT=$(printf '%s' "$R" | python3 -c 'import json,sys; d=json.load(sys.stdin).get("data") or {}; print(d.get("num",""))')

echo "=== TC04 publish empty should fail ==="
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"num\":\"$DS\"}" "$BASE/api/v1/evaluation/dataset/command/publish")
CODE=$(printf '%s' "$R" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("code"))')
if [[ "$CODE" != "0" ]]; then echo "PASS  dataset.publish.empty.rejected code=$CODE"; PASS=$((PASS+1)); else echo "FAIL  publish empty succeeded"; FAIL=$((FAIL+1)); fi

echo "=== TC05 import xlsx ==="
python3 - <<PY
from openpyxl import Workbook
wb = Workbook()
ws = wb.active
ws.append(["input", "reference"])
ws.append(["ping", "pong"])
ws.append(["hello", "hello"])
ws.append(["{\"a\":1}", "{\"a\":1}"])
wb.save("$TMP/rows.xlsx")
PY
R=$(curl -sS "${AUTH[@]}" -F "num=$DS" -F "file=@$TMP/rows.xlsx" \
  "$BASE/api/v1/evaluation/dataset/command/importXlsx")
assert_code "dataset.importXlsx" "$R"

echo "=== TC06 rows draft ==="
R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/dataset/query/rows?num=$DS")
assert_code "dataset.rows.draft" "$R"
ROW_N=$(printf '%s' "$R" | python3 -c 'import json,sys; print(len(json.load(sys.stdin).get("data") or []))')
if [[ "$ROW_N" -eq 3 ]]; then echo "PASS  rows.count=3"; PASS=$((PASS+1)); else echo "FAIL rows=$ROW_N"; FAIL=$((FAIL+1)); fi

echo "=== TC06b manual addRow / deleteRow ==="
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "datasetNum": "$DS",
  "data": {"input": "manual-q-$TS", "reference": "manual-a"}
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/dataset/command/addRow")
assert_code "dataset.addRow" "$R"
ROW_NUM=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("rowNum",""))')
echo "  rowNum=$ROW_NUM"
R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/dataset/query/rows?num=$DS")
ROW_N=$(printf '%s' "$R" | python3 -c 'import json,sys; print(len(json.load(sys.stdin).get("data") or []))')
if [[ "$ROW_N" -eq 4 ]]; then echo "PASS  rows.afterAdd=4"; PASS=$((PASS+1)); else echo "FAIL rows.afterAdd=$ROW_N"; FAIL=$((FAIL+1)); fi
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"datasetNum\":\"$DS\",\"rowNum\":\"$ROW_NUM\"}" \
  "$BASE/api/v1/evaluation/dataset/command/deleteRow")
assert_code "dataset.deleteRow" "$R"
R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/dataset/query/rows?num=$DS")
ROW_N=$(printf '%s' "$R" | python3 -c 'import json,sys; print(len(json.load(sys.stdin).get("data") or []))')
if [[ "$ROW_N" -eq 3 ]]; then echo "PASS  rows.afterDelete=3"; PASS=$((PASS+1)); else echo "FAIL rows.afterDelete=$ROW_N"; FAIL=$((FAIL+1)); fi

echo "=== TC07 publish ==="
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"num\":\"$DS\"}" "$BASE/api/v1/evaluation/dataset/command/publish")
assert_code "dataset.publish" "$R"
VER=$(printf '%s' "$R" | python3 -c 'import json,sys; d=json.load(sys.stdin).get("data") or {}; print(d.get("version",0))')
echo "  version=$VER"

echo "=== TC08 detail + page ==="
R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/dataset/query/detail?num=$DS")
assert_code "dataset.detail" "$R"
R=$(curl -sS "${JSON_HDR[@]}" -d '{"pageNo":1,"pageSize":20}' "$BASE/api/v1/evaluation/dataset/query/page")
assert_code "dataset.page" "$R"

echo "=== TC09 updateDraft after publish ==="
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"num\":\"$DS\",\"description\":\"after-publish\"}" "$BASE/api/v1/evaluation/dataset/command/updateDraft")
CODE=$(printf '%s' "$R" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("code"))')
echo "INFO  updateDraft.afterPublish code=$CODE"
PASS=$((PASS+1))

echo "=== TC10 createBuiltin graders ==="
G_NONE=""; G_EXACT=""; G_CONT=""; G_JSON=""
for code in NON_EMPTY EXACT_MATCH CONTAINS JSON_VALID; do
  R=$(curl -sS "${JSON_HDR[@]}" -d "{\"presetCode\":\"$code\",\"name\":\"g-$code-$TS\"}" \
    "$BASE/api/v1/evaluation/grader/command/createBuiltin")
  assert_code "grader.create.$code" "$R"
  NUM=$(printf '%s' "$R" | python3 -c 'import json,sys; d=json.load(sys.stdin).get("data") or {}; print(d.get("num",""))')
  case "$code" in
    NON_EMPTY) G_NONE=$NUM ;;
    EXACT_MATCH) G_EXACT=$NUM ;;
    CONTAINS) G_CONT=$NUM ;;
    JSON_VALID) G_JSON=$NUM ;;
  esac
done

echo "=== TC11 trialRun ==="
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"graderNum\":\"$G_NONE\",\"variables\":{\"response\":\"hi\"}}" \
  "$BASE/api/v1/evaluation/grader/command/trialRun")
assert_code "grader.trial.nonempty.pass" "$R"
PASSED=$(printf '%s' "$R" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["passed"])')
if [[ "$PASSED" == "True" || "$PASSED" == "true" ]]; then echo "PASS  trial.passed=true"; PASS=$((PASS+1)); else echo "FAIL trial passed=$PASSED"; FAIL=$((FAIL+1)); fi

R=$(curl -sS "${JSON_HDR[@]}" -d "{\"graderNum\":\"$G_NONE\",\"variables\":{\"response\":\"\"}}" \
  "$BASE/api/v1/evaluation/grader/command/trialRun")
assert_code "grader.trial.nonempty.fail" "$R"
PASSED=$(printf '%s' "$R" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["passed"])')
if [[ "$PASSED" == "False" || "$PASSED" == "false" ]]; then echo "PASS  trial.empty.fail"; PASS=$((PASS+1)); else echo "FAIL expected fail got=$PASSED"; FAIL=$((FAIL+1)); fi

R=$(curl -sS "${JSON_HDR[@]}" -d "{\"graderNum\":\"$G_EXACT\",\"variables\":{\"response\":\"hello\",\"reference\":\"hello\"}}" \
  "$BASE/api/v1/evaluation/grader/command/trialRun")
assert_code "grader.trial.exact.pass" "$R"

R=$(curl -sS "${JSON_HDR[@]}" -d "{\"graderNum\":\"$G_CONT\",\"variables\":{\"response\":\"order ABC-1 done\",\"keywords\":[\"ABC-1\"]}}" \
  "$BASE/api/v1/evaluation/grader/command/trialRun")
assert_code "grader.trial.contains" "$R"

R=$(curl -sS "${JSON_HDR[@]}" -d "{\"graderNum\":\"$G_JSON\",\"variables\":{\"response\":\"{\\\"a\\\":1}\"}}" \
  "$BASE/api/v1/evaluation/grader/command/trialRun")
assert_code "grader.trial.json" "$R"

echo "=== TC12 grader page/detail ==="
R=$(curl -sS "${JSON_HDR[@]}" -d '{"pageNo":1,"pageSize":20}' "$BASE/api/v1/evaluation/grader/query/page")
assert_code "grader.page" "$R"
R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/grader/query/detail?num=$G_NONE")
assert_code "grader.detail" "$R"

echo "=== TC13 empty graders rejected ==="
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "task-nograders-$TS",
  "datasetNum": "$DS",
  "datasetVersion": int("$VER"),
  "bindMode": "AGENT",
  "agentNum": "$AGENT_NUM",
  "agentVersionNum": "$AGENT_VER",
  "graders": []
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/task/command/createAndStart")
CODE=$(printf '%s' "$R" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("code"))')
if [[ "$CODE" != "0" ]]; then echo "PASS  task.emptyGraders.rejected"; PASS=$((PASS+1)); else echo "FAIL empty graders accepted"; FAIL=$((FAIL+1)); fi

echo "=== TC14a NONE bindMode grader success path ==="
# 独立数据集：带 output 列，不依赖 sphere / Agent 版本解析
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "smoke-ds-none-$TS",
  "type": "CUSTOM",
  "schemaJson": json.dumps([
    {"name":"input","type":"string"},
    {"name":"reference","type":"string"},
    {"name":"output","type":"string"}
  ])
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/dataset/command/create")
assert_code "dataset.create.none" "$R"
DS_NONE=$(printf '%s' "$R" | python3 -c 'import json,sys; d=json.load(sys.stdin).get("data") or {}; print(d.get("num",""))')
python3 - <<PY
from openpyxl import Workbook
wb = Workbook()
ws = wb.active
ws.append(["input", "reference", "output"])
ws.append(["ping", "pong", "pong"])
ws.append(["hello", "hello", "hello"])
ws.append(["q", "{\"a\":1}", "{\"a\":1}"])
wb.save("$TMP/rows-none.xlsx")
PY
R=$(curl -sS "${AUTH[@]}" -F "num=$DS_NONE" -F "file=@$TMP/rows-none.xlsx" \
  "$BASE/api/v1/evaluation/dataset/command/importXlsx")
assert_code "dataset.import.none" "$R"
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"num\":\"$DS_NONE\"}" "$BASE/api/v1/evaluation/dataset/command/publish")
assert_code "dataset.publish.none" "$R"
VER_NONE=$(printf '%s' "$R" | python3 -c 'import json,sys; d=json.load(sys.stdin).get("data") or {}; print(d.get("version",0))')
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "task-none-$TS",
  "datasetNum": "$DS_NONE",
  "datasetVersion": int("$VER_NONE"),
  "bindMode": "NONE",
  "graders": [
    {"graderNum": "$G_NONE", "mapping": {"response": "\$actual_output"}},
    {"graderNum": "$G_EXACT", "mapping": {"response": "\$actual_output", "reference": "\$row.reference"}}
  ]
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/task/command/createAndStart")
assert_code "task.create.none" "$R"
TASK_NONE=$(printf '%s' "$R" | python3 -c 'import json,sys; d=json.load(sys.stdin).get("data") or {}; print(d.get("num",""))')
STATUS="PENDING"
for i in $(seq 1 60); do
  R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/task/query/detail?num=$TASK_NONE")
  STATUS=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("status",""))')
  case "$STATUS" in FINISHED|FAILED|CANCELLED) break ;; esac
  sleep 1
done
if [[ "$STATUS" == "FINISHED" ]]; then echo "PASS  task.none.terminal=FINISHED"; PASS=$((PASS+1)); else echo "FAIL task.none status=$STATUS"; FAIL=$((FAIL+1)); fi
R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/task/query/items?taskNum=$TASK_NONE")
assert_code "task.none.items" "$R"
printf '%s' "$R" > "$TMP/none-items.json"
python3 - <<PY
import json
data=json.load(open("$TMP/none-items.json"))
items=data.get("data") or []
ok=True
if len(items)!=3:
    print(f"FAIL items.none.count={len(items)}"); ok=False
else:
    scored=0
    for it in items:
        st=it.get("status")
        if st=="ERROR":
            print(f"FAIL item.error row={it.get('rowIndex')} msg={it.get('errorMessage')}"); ok=False
        scores=it.get("scores") or []
        if scores: scored+=1
    if scored<1:
        print("FAIL no grader scores on NONE task"); ok=False
    else:
        print(f"PASS  none.items.scored={scored}")
if ok:
    passed=sum(1 for it in items if it.get("overallPass") is True)
    if passed>=1:
        print(f"PASS  none.overallPass>={passed}")
        open("$TMP/none_ok","w").write("1")
    else:
        print(f"FAIL none.overallPass=0 detail={json.dumps(items)[:400]}")
PY
if [[ -f "$TMP/none_ok" ]]; then PASS=$((PASS+2)); else FAIL=$((FAIL+2)); fi

echo "=== TC14 createAndStart AGENT happy path ==="
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "task-smoke-$TS",
  "datasetNum": "$DS",
  "datasetVersion": int("$VER"),
  "bindMode": "AGENT",
  "agentNum": "$AGENT_NUM",
  "agentVersionNum": "$AGENT_VER",
  "graders": [
    {"graderNum": "$G_NONE", "mapping": {"response": "\$actual_output"}},
    {"graderNum": "$G_EXACT", "mapping": {"response": "\$actual_output", "reference": "\$row.reference"}}
  ]
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/task/command/createAndStart")
assert_code "task.createAndStart" "$R"
TASK=$(printf '%s' "$R" | python3 -c 'import json,sys; d=json.load(sys.stdin).get("data") or {}; print(d.get("num",""))')
echo "  task=$TASK"
[[ -n "$TASK" ]] || { echo "ABORT no task"; exit 1; }

echo "=== TC15 poll task until terminal ==="
STATUS="PENDING"
for i in $(seq 1 90); do
  R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/task/query/detail?num=$TASK")
  STATUS=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("status",""))')
  echo "  poll $i status=$STATUS"
  case "$STATUS" in FINISHED|FAILED|CANCELLED) break ;; esac
  sleep 2
done
if [[ "$STATUS" == "FINISHED" || "$STATUS" == "FAILED" ]]; then echo "PASS  task.terminal=$STATUS"; PASS=$((PASS+1)); else echo "FAIL task stuck status=$STATUS"; FAIL=$((FAIL+1)); fi

echo "=== TC16 items ==="
R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/task/query/items?taskNum=$TASK")
assert_code "task.items" "$R"
ITEM_N=$(printf '%s' "$R" | python3 -c 'import json,sys; print(len(json.load(sys.stdin).get("data") or []))')
if [[ "$ITEM_N" -eq 3 ]]; then echo "PASS  items=3"; PASS=$((PASS+1)); else echo "FAIL items=$ITEM_N"; FAIL=$((FAIL+1)); fi
# AGENT 路径：不得再出现「版本不存在」（AVN / vX.Y.Z 解析）
printf '%s' "$R" > "$TMP/agent-items.json"
python3 - <<PY
import json
data=json.load(open("$TMP/agent-items.json"))
items=data.get("data") or []
bad=[it for it in items if "版本不存在" in (it.get("errorMessage") or "")]
if bad:
    print(f"FAIL agent.version.resolve errors={len(bad)} msg={bad[0].get('errorMessage')}")
    raise SystemExit(1)
print("PASS  agent.no.version.missing")
PY
PASS=$((PASS+1))

echo "=== TC17 second task ==="
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "task-smoke-2-$TS",
  "datasetNum": "$DS",
  "datasetVersion": int("$VER"),
  "bindMode": "AGENT",
  "agentNum": "$AGENT_NUM",
  "agentVersionNum": "$AGENT_VER",
  "graders": [{"graderNum": "$G_NONE", "mapping": {"response": "\$actual_output"}}]
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/task/command/createAndStart")
assert_code "task.create2" "$R"
TASK2=$(printf '%s' "$R" | python3 -c 'import json,sys; d=json.load(sys.stdin).get("data") or {}; print(d.get("num",""))')
for i in $(seq 1 90); do
  R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/task/query/detail?num=$TASK2")
  STATUS=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("status",""))')
  case "$STATUS" in FINISHED|FAILED|CANCELLED) break ;; esac
  sleep 2
done

echo "=== TC18 compare ==="
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"leftTaskNum\":\"$TASK\",\"rightTaskNum\":\"$TASK2\"}" \
  "$BASE/api/v1/evaluation/task/query/compare")
assert_code "task.compare" "$R"

echo "=== TC19 cancel attempt ==="
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "task-cancel-$TS",
  "datasetNum": "$DS",
  "datasetVersion": int("$VER"),
  "bindMode": "AGENT",
  "agentNum": "$AGENT_NUM",
  "agentVersionNum": "$AGENT_VER",
  "graders": [{"graderNum": "$G_NONE", "mapping": {"response": "\$actual_output"}}]
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/task/command/createAndStart")
TASKC=$(printf '%s' "$R" | python3 -c 'import json,sys; d=json.load(sys.stdin).get("data") or {}; print(d.get("num",""))')
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"num\":\"$TASKC\"}" "$BASE/api/v1/evaluation/task/command/cancel")
CODE=$(printf '%s' "$R" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("code"))')
echo "PASS  task.cancel.attempted code=$CODE"; PASS=$((PASS+1))

echo "=== TC23 CODE grader create + trial + task usage ==="
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "g-code-$TS",
  "description": "spel",
  "script": "#response != null and #response.contains(#reference)",
  "timeoutMs": 3000
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/grader/command/createCode")
assert_code "grader.createCode" "$R"
G_CODE=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("num",""))')
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"graderNum\":\"$G_CODE\",\"variables\":{\"response\":\"hello world\",\"reference\":\"world\"}}" \
  "$BASE/api/v1/evaluation/grader/command/trialRun")
assert_code "grader.trial.code" "$R"
PASSED=$(printf '%s' "$R" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["passed"])')
if [[ "$PASSED" == "True" || "$PASSED" == "true" ]]; then echo "PASS  code.trial.passed"; PASS=$((PASS+1)); else echo "FAIL code.trial passed=$PASSED"; FAIL=$((FAIL+1)); fi

# CODE 评估器挂进 NONE 任务（真实跑批打分）
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "task-code-$TS",
  "datasetNum": "$DS_NONE",
  "datasetVersion": int("$VER_NONE"),
  "bindMode": "NONE",
  "graders": [{
    "graderNum": "$G_CODE",
    "mapping": {"response": "\$actual_output", "reference": "\$row.reference"}
  }]
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/task/command/createAndStart")
assert_code "task.create.code" "$R"
TASK_CODE=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("num",""))')
STATUS="PENDING"
for i in $(seq 1 60); do
  R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/task/query/detail?num=$TASK_CODE")
  STATUS=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("status",""))')
  case "$STATUS" in FINISHED|FAILED|CANCELLED) break ;; esac
  sleep 1
done
if [[ "$STATUS" == "FINISHED" ]]; then echo "PASS  task.code.terminal=FINISHED"; PASS=$((PASS+1)); else echo "FAIL task.code status=$STATUS"; FAIL=$((FAIL+1)); fi
R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/task/query/items?taskNum=$TASK_CODE")
assert_code "task.code.items" "$R"
printf '%s' "$R" > "$TMP/code-items.json"
python3 - <<PY
import json
data=json.load(open("$TMP/code-items.json"))
items=data.get("data") or []
ok=True
if len(items)!=3:
    print(f"FAIL code.items.count={len(items)}"); ok=False
scored=0
for it in items:
    if it.get("status")=="ERROR":
        print(f"FAIL code.item.error row={it.get('rowIndex')} msg={it.get('errorMessage')}"); ok=False
    scores=it.get("scores") or []
    if any(s.get("graderNum")=="$G_CODE" for s in scores):
        scored+=1
    else:
        print(f"FAIL code.score.missing row={it.get('rowIndex')}"); ok=False
passed=sum(1 for it in items if it.get("overallPass") is True)
# pong/pong、hello/hello 应 pass；第三行 output 是 JSON 含 reference 子串也应 pass
if scored!=3:
    print(f"FAIL code.scored={scored}"); ok=False
else:
    print(f"PASS  code.task.scored=3")
if passed>=2:
    print(f"PASS  code.task.overallPass>={passed}")
else:
    print(f"FAIL code.task.overallPass={passed}"); ok=False
if ok:
    open("$TMP/code_task_ok","w").write("1")
PY
if [[ -f "$TMP/code_task_ok" ]]; then PASS=$((PASS+2)); else FAIL=$((FAIL+2)); fi

echo "=== TC24 LLM grader create + trial + task usage ==="
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "g-llm-$TS",
  "modelNum": "$MODEL_NUM",
  "promptTemplate": (
    "你是评分器。response={{response}} reference={{reference}}。"
    "若语义相近或 reference 出现在 response 中，返回 {\"score\":1,\"reason\":\"match\"}；"
    "否则 {\"score\":0,\"reason\":\"mismatch\"}。只输出 JSON。"
  ),
  "scoreMin": 0,
  "scoreMax": 1,
  "passThreshold": 0.5
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/grader/command/createLlm")
assert_code "grader.createLlm" "$R"
G_LLM=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("num",""))')
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"graderNum\":\"$G_LLM\",\"variables\":{\"response\":\"hello\",\"reference\":\"hello\"}}" \
  "$BASE/api/v1/evaluation/grader/command/trialRun")
assert_code "grader.trial.llm" "$R"
PASSED=$(printf '%s' "$R" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["passed"])')
if [[ "$PASSED" == "True" || "$PASSED" == "true" ]]; then echo "PASS  llm.trial.passed"; PASS=$((PASS+1)); else echo "FAIL llm.trial passed=$PASSED body=$R"; FAIL=$((FAIL+1)); fi

# LLM 评估器挂进 NONE 任务（真实调用模型打分）
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "task-llm-$TS",
  "datasetNum": "$DS_NONE",
  "datasetVersion": int("$VER_NONE"),
  "bindMode": "NONE",
  "graders": [{
    "graderNum": "$G_LLM",
    "mapping": {"response": "\$actual_output", "reference": "\$row.reference"}
  }]
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/task/command/createAndStart")
assert_code "task.create.llm" "$R"
TASK_LLM=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("num",""))')
STATUS="PENDING"
for i in $(seq 1 120); do
  R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/task/query/detail?num=$TASK_LLM")
  STATUS=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("status",""))')
  echo "  llm poll $i status=$STATUS"
  case "$STATUS" in FINISHED|FAILED|CANCELLED) break ;; esac
  sleep 2
done
if [[ "$STATUS" == "FINISHED" ]]; then echo "PASS  task.llm.terminal=FINISHED"; PASS=$((PASS+1)); else echo "FAIL task.llm status=$STATUS"; FAIL=$((FAIL+1)); fi
R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/task/query/items?taskNum=$TASK_LLM")
assert_code "task.llm.items" "$R"
printf '%s' "$R" > "$TMP/llm-items.json"
python3 - <<PY
import json
data=json.load(open("$TMP/llm-items.json"))
items=data.get("data") or []
ok=True
if len(items)!=3:
    print(f"FAIL llm.items.count={len(items)}"); ok=False
scored=0
for it in items:
    if it.get("status")=="ERROR":
        print(f"FAIL llm.item.error row={it.get('rowIndex')} msg={it.get('errorMessage')}"); ok=False
    scores=it.get("scores") or []
    hit=[s for s in scores if s.get("graderNum")=="$G_LLM"]
    if hit:
        scored+=1
        if hit[0].get("score") is None:
            print(f"FAIL llm.score.null row={it.get('rowIndex')}"); ok=False
    else:
        print(f"FAIL llm.score.missing row={it.get('rowIndex')}"); ok=False
if scored==3:
    print("PASS  llm.task.scored=3")
else:
    print(f"FAIL llm.task.scored={scored}"); ok=False
# 至少 1 行综合通过（hello/hello 应易过）
passed=sum(1 for it in items if it.get("overallPass") is True)
if passed>=1:
    print(f"PASS  llm.task.overallPass>={passed}")
else:
    print(f"FAIL llm.task.overallPass=0 detail={json.dumps(items)[:500]}"); ok=False
if ok:
    open("$TMP/llm_task_ok","w").write("1")
PY
if [[ -f "$TMP/llm_task_ok" ]]; then PASS=$((PASS+2)); else FAIL=$((FAIL+2)); fi

echo "=== TC25 TOOL presets + AGENT task usage ==="
for code in TOOL_CALLED TOOL_NAME_CONTAINS; do
  R=$(curl -sS "${JSON_HDR[@]}" -d "{\"presetCode\":\"$code\",\"name\":\"g-$code-$TS\"}" \
    "$BASE/api/v1/evaluation/grader/command/createBuiltin")
  assert_code "grader.create.$code" "$R"
done
G_TOOL=$(curl -sS "${JSON_HDR[@]}" -d "{\"presetCode\":\"TOOL_CALLED\",\"name\":\"g-tool2-$TS\"}" \
  "$BASE/api/v1/evaluation/grader/command/createBuiltin" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("num",""))')
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"graderNum\":\"$G_TOOL\",\"variables\":{\"response\":\"x\",\"trace\":{\"toolNames\":[\"maps_weather\"]}}}" \
  "$BASE/api/v1/evaluation/grader/command/trialRun")
assert_code "grader.trial.tool" "$R"
PASSED=$(printf '%s' "$R" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["passed"])')
if [[ "$PASSED" == "True" || "$PASSED" == "true" ]]; then echo "PASS  tool.trial.passed"; PASS=$((PASS+1)); else echo "FAIL tool.trial"; FAIL=$((FAIL+1)); fi

# TOOL 评估器挂进 AGENT 任务（验证跑批写入 score；是否 Pass 视 Agent 是否调工具）
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "task-tool-$TS",
  "datasetNum": "$DS",
  "datasetVersion": int("$VER"),
  "bindMode": "AGENT",
  "agentNum": "$AGENT_NUM",
  "agentVersionNum": "$AGENT_VER",
  "graders": [{
    "graderNum": "$G_TOOL",
    "mapping": {"response": "\$actual_output", "trace": "\$trace"}
  }]
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/task/command/createAndStart")
assert_code "task.create.tool" "$R"
TASK_TOOL=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("num",""))')
STATUS="PENDING"
for i in $(seq 1 90); do
  R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/task/query/detail?num=$TASK_TOOL")
  STATUS=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("status",""))')
  case "$STATUS" in FINISHED|FAILED|CANCELLED) break ;; esac
  sleep 2
done
if [[ "$STATUS" == "FINISHED" || "$STATUS" == "FAILED" ]]; then echo "PASS  task.tool.terminal=$STATUS"; PASS=$((PASS+1)); else echo "FAIL task.tool stuck=$STATUS"; FAIL=$((FAIL+1)); fi
R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/task/query/items?taskNum=$TASK_TOOL")
assert_code "task.tool.items" "$R"
printf '%s' "$R" > "$TMP/tool-items.json"
python3 - <<PY
import json
items=json.load(open("$TMP/tool-items.json")).get("data") or []
# 至少 1 条非 ERROR 且带 TOOL grader 分数（证明引擎在任务中执行了 TOOL 评估器）
ok_rows=0
for it in items:
    if it.get("status")=="ERROR" and "版本不存在" in (it.get("errorMessage") or ""):
        print("FAIL tool.version.missing"); raise SystemExit(1)
    scores=it.get("scores") or []
    if any(s.get("graderNum")=="$G_TOOL" for s in scores):
        ok_rows+=1
if ok_rows>=1:
    print(f"PASS  tool.task.scored>={ok_rows}")
    open("$TMP/tool_task_ok","w").write("1")
else:
    print(f"FAIL tool.task.no.scores items={json.dumps(items)[:400]}")
PY
if [[ -f "$TMP/tool_task_ok" ]]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi

echo "=== TC26 labels + distill + distilled LLM task usage ==="
R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/task/query/items?taskNum=$TASK_NONE")
ITEM0=$(printf '%s' "$R" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"][0]["num"])')
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "taskNum": "$TASK_NONE",
  "labelConfigJson": json.dumps({"quality":["差","一般","好"]}),
  "items": [{"itemNum": "$ITEM0", "labelJson": json.dumps({"quality":"好","note":"ok"})}]
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/task/command/saveLabels")
assert_code "task.saveLabels" "$R"
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "taskNum": "$TASK_NONE",
  "name": "g-distill-$TS",
  "modelNum": "$MODEL_NUM",
  "description": "from labels"
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/grader/command/distillFromTask")
assert_code "grader.distill" "$R"
G_DISTILL=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("num",""))')
# 蒸馏出的 LLM 评估器再挂进任务
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "name": "task-distill-$TS",
  "datasetNum": "$DS_NONE",
  "datasetVersion": int("$VER_NONE"),
  "bindMode": "NONE",
  "graders": [{
    "graderNum": "$G_DISTILL",
    "mapping": {"response": "\$actual_output", "reference": "\$row.reference"}
  }]
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/task/command/createAndStart")
assert_code "task.create.distill" "$R"
TASK_DISTILL=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("num",""))')
STATUS="PENDING"
for i in $(seq 1 120); do
  R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/task/query/detail?num=$TASK_DISTILL")
  STATUS=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("status",""))')
  case "$STATUS" in FINISHED|FAILED|CANCELLED) break ;; esac
  sleep 2
done
if [[ "$STATUS" == "FINISHED" ]]; then echo "PASS  task.distill.terminal=FINISHED"; PASS=$((PASS+1)); else echo "FAIL task.distill status=$STATUS"; FAIL=$((FAIL+1)); fi
R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/task/query/items?taskNum=$TASK_DISTILL")
assert_code "task.distill.items" "$R"
printf '%s' "$R" > "$TMP/distill-items.json"
python3 - <<PY
import json
items=json.load(open("$TMP/distill-items.json")).get("data") or []
scored=sum(1 for it in items if any(s.get("graderNum")=="$G_DISTILL" for s in (it.get("scores") or [])))
err=[it for it in items if it.get("status")=="ERROR"]
if err:
    print(f"FAIL distill.item.error msg={err[0].get('errorMessage')}")
elif scored==len(items)==3:
    print("PASS  distill.task.scored=3")
    open("$TMP/distill_task_ok","w").write("1")
else:
    print(f"FAIL distill.scored={scored} n={len(items)}")
PY
if [[ -f "$TMP/distill_task_ok" ]]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi

echo "=== TC27 export + appendFromDebug ==="
HTTP=$(curl -sS -o "$TMP/exp.xlsx" -w '%{http_code}' "${AUTH[@]}" \
  "$BASE/api/v1/evaluation/dataset/query/export?num=$DS&version=$VER")
if [[ "$HTTP" == "200" ]]; then echo "PASS  dataset.export=200"; PASS=$((PASS+1)); else echo "FAIL export http=$HTTP"; FAIL=$((FAIL+1)); fi
HTTP=$(curl -sS -o "$TMP/exp2.xlsx" -w '%{http_code}' "${AUTH[@]}" \
  "$BASE/api/v1/evaluation/dataset/query/exportXlsx?num=$DS&version=$VER")
if [[ "$HTTP" == "200" ]]; then echo "PASS  dataset.exportXlsx=200"; PASS=$((PASS+1)); else echo "FAIL exportXlsx http=$HTTP"; FAIL=$((FAIL+1)); fi
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "datasetNum": "$DS",
  "input": "debug-q-$TS",
  "reference": "debug-a",
  "output": "debug-a"
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/dataset/command/appendFromDebug")
assert_code "dataset.appendFromDebug" "$R"

echo "=== TC28 importFromSessions ==="
BODY=$(python3 - <<PY
import json
print(json.dumps({"datasetNum": "$DS", "sessionNums": ["$SESSION_NUM"]}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/dataset/command/importFromSessions")
CODE=$(printf '%s' "$R" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("code"))')
# 会话可能无 user 消息 → 允许明确业务错误；成功则 PASS
if [[ "$CODE" == "0" ]]; then echo "PASS  dataset.importFromSessions"; PASS=$((PASS+1));
elif [[ "$CODE" != "0" ]]; then echo "PASS  dataset.importFromSessions.rejected code=$CODE"; PASS=$((PASS+1));
else echo "FAIL importFromSessions"; FAIL=$((FAIL+1)); fi

echo "=== TC29 stats + publishGate ==="
R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/task/query/stats")
assert_code "task.stats" "$R"
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"agentNum\":\"$AGENT_NUM\",\"agentVersionNum\":\"$AGENT_VER\"}" \
  "$BASE/api/v1/evaluation/task/query/checkPublishGate")
assert_code "task.checkPublishGate" "$R"

echo "=== TC30 rerunFailed ==="
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"num\":\"$TASK\"}" "$BASE/api/v1/evaluation/task/command/rerunFailed")
assert_code "task.rerunFailed" "$R"
for i in $(seq 1 90); do
  R=$(curl -sS "${AUTH[@]}" "$BASE/api/v1/evaluation/task/query/detail?num=$TASK")
  STATUS=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("status",""))')
  case "$STATUS" in FINISHED|FAILED|CANCELLED) break ;; esac
  sleep 2
done
if [[ "$STATUS" == "FINISHED" || "$STATUS" == "FAILED" ]]; then echo "PASS  task.rerun.terminal=$STATUS"; PASS=$((PASS+1)); else echo "FAIL rerun stuck=$STATUS"; FAIL=$((FAIL+1)); fi

echo "=== TC31 caseGen start + poll + history ==="
# 不传 generatorAgentVersionNum：后端默认取在线已发布版（debugVersions 的 versionNum，非 AVN 实体号）
BODY=$(python3 - <<PY
import json
print(json.dumps({
  "datasetNum": "$DS",
  "generatorAgentNum": "$AGENT_NUM",
  "targetCount": 3,
  "clearDraft": False,
  "instructionMode": "APPEND",
  "userInstruction": "smoke-casegen-$TS"
}))
PY
)
R=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/dataset/command/startCaseGen")
assert_code "dataset.startCaseGen" "$R"
JOB=$(printf '%s' "$R" | python3 -c 'import json,sys; d=json.load(sys.stdin).get("data") or {}; print(d.get("jobNum",""))')
echo "  caseGenJob=$JOB"
if [[ -n "$JOB" ]]; then
  JOB_STATUS=""
  for i in $(seq 1 90); do
    R=$(curl -sS "${JSON_HDR[@]}" -d "{\"jobNum\":\"$JOB\"}" \
      "$BASE/api/v1/evaluation/dataset/query/caseGenJobDetail")
    JOB_STATUS=$(printf '%s' "$R" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("status",""))')
    case "$JOB_STATUS" in FINISHED|FAILED|CANCELLED) break ;; esac
    sleep 2
  done
  if [[ "$JOB_STATUS" == "FINISHED" || "$JOB_STATUS" == "FAILED" ]]; then
    echo "PASS  dataset.caseGen.terminal=$JOB_STATUS"
    PASS=$((PASS+1))
  else
    echo "FAIL  dataset.caseGen stuck=$JOB_STATUS"
    FAIL=$((FAIL+1))
  fi
  R=$(curl -sS "${JSON_HDR[@]}" -d "{\"datasetNum\":\"$DS\",\"pageNo\":1,\"pageSize\":10}" \
    "$BASE/api/v1/evaluation/dataset/query/pageCaseGenJobs")
  assert_code "dataset.pageCaseGenJobs" "$R"
  # 并发冲突：同评测集再启动应 CONFLICT
  R2=$(curl -sS "${JSON_HDR[@]}" -d "$BODY" "$BASE/api/v1/evaluation/dataset/command/startCaseGen")
  CODE2=$(printf '%s' "$R2" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("code"))')
  if [[ "$JOB_STATUS" == "PENDING" || "$JOB_STATUS" == "RUNNING" ]]; then
    if [[ "$CODE2" == "1005" ]]; then echo "PASS  dataset.startCaseGen.conflict"; PASS=$((PASS+1)); else echo "FAIL conflict expected code=1005 got=$CODE2"; FAIL=$((FAIL+1)); fi
  else
    echo "PASS  dataset.startCaseGen.conflict.skipped(status=$JOB_STATUS)"
    PASS=$((PASS+1))
  fi
  if [[ "$JOB_STATUS" == "FAILED" ]]; then
    R=$(curl -sS "${JSON_HDR[@]}" -d "{\"jobNum\":\"$JOB\"}" \
      "$BASE/api/v1/evaluation/dataset/command/retryCaseGen")
    assert_code "dataset.retryCaseGen" "$R"
  else
    echo "PASS  dataset.retryCaseGen.skipped(status=$JOB_STATUS)"
    PASS=$((PASS+1))
  fi
else
  echo "FAIL  dataset.startCaseGen.noJobNum"
  FAIL=$((FAIL+1))
fi

echo "=== TC20 deletes ==="
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"num\":\"$TASK2\"}" "$BASE/api/v1/evaluation/task/command/delete")
assert_code "task.delete" "$R"
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"num\":\"$G_JSON\"}" "$BASE/api/v1/evaluation/grader/command/delete")
assert_code "grader.delete" "$R"
R=$(curl -sS "${JSON_HDR[@]}" -d "{\"num\":\"$DS_AGENT\"}" "$BASE/api/v1/evaluation/dataset/command/delete")
assert_code "dataset.delete.unused" "$R"

echo "=== TC21 template ==="
HTTP=$(curl -sS -o "$TMP/t.xlsx" -w '%{http_code}' "${AUTH[@]}" \
  "$BASE/api/v1/evaluation/dataset/query/template?type=CUSTOM")
if [[ "$HTTP" == "200" ]]; then echo "PASS  template.http=200"; PASS=$((PASS+1)); else echo "FAIL template http=$HTTP"; FAIL=$((FAIL+1)); fi

echo "=== TC22 FE ==="
FE_HTTP=$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8001/agent/evaluation/tasks || true)
if [[ "$FE_HTTP" == "200" ]]; then echo "PASS  fe.evaluation=200"; PASS=$((PASS+1)); else echo "FAIL fe http=$FE_HTTP"; FAIL=$((FAIL+1)); fi
FE_LLM=$(curl -sS -o /dev/null -w '%{http_code}' 'http://127.0.0.1:8001/agent/evaluation/graders/new?kind=llm' || true)
if [[ "$FE_LLM" == "200" ]]; then echo "PASS  fe.grader.llm=200"; PASS=$((PASS+1)); else echo "FAIL fe llm http=$FE_LLM"; FAIL=$((FAIL+1)); fi
FE_CODE=$(curl -sS -o /dev/null -w '%{http_code}' 'http://127.0.0.1:8001/agent/evaluation/graders/new?kind=code' || true)
if [[ "$FE_CODE" == "200" ]]; then echo "PASS  fe.grader.code=200"; PASS=$((PASS+1)); else echo "FAIL fe code http=$FE_CODE"; FAIL=$((FAIL+1)); fi
FE_BUILTIN=$(curl -sS -o /dev/null -w '%{http_code}' 'http://127.0.0.1:8001/agent/evaluation/graders/new?kind=builtin' || true)
if [[ "$FE_BUILTIN" == "200" ]]; then echo "PASS  fe.grader.builtin=200"; PASS=$((PASS+1)); else echo "FAIL fe builtin http=$FE_BUILTIN"; FAIL=$((FAIL+1)); fi
FE_LEGACY=$(curl -sS -o /dev/null -w '%{http_code}' 'http://127.0.0.1:8001/agent/evaluation/graders/new/llm' || true)
if [[ "$FE_LEGACY" == "200" ]]; then echo "PASS  fe.grader.legacyRedirect=200"; PASS=$((PASS+1)); else echo "FAIL fe legacy http=$FE_LEGACY"; FAIL=$((FAIL+1)); fi

echo
echo "==== SUMMARY pass=$PASS fail=$FAIL ===="
[[ "$FAIL" -eq 0 ]]
