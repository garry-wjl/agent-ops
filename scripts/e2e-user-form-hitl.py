#!/usr/bin/env python3
"""
本地集成：调试台 invoke SSE → 挂起 AGENT_RESULT → user_input.required
→ respond → accepted → 续跑帧 → run_finished。

验证点：挂起后连接仍可收心跳/后续帧（未因 AGENT_RESULT 提前结束）。
"""
from __future__ import annotations

import json
import sys
import threading
import time
import urllib.error
import urllib.request
from typing import Any

BASE = "http://127.0.0.1:8081"
WS = "WS-1997fd034709"
AGT = "AGT2026080922497568"
TARGET_VERSION = "DRAFT"


def http_json(method: str, path: str, body: dict | None = None, timeout: float = 60) -> Any:
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        BASE + path,
        data=data,
        method=method,
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
            "X-Workspace-Num": WS,
        },
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def parse_sse_chunk(buf: str) -> tuple[list[dict], str]:
    events: list[dict] = []
    while "\n\n" in buf:
        block, buf = buf.split("\n\n", 1)
        if not block.strip() or block.strip().startswith(":"):
            continue
        ev: dict[str, Any] = {"event": "message", "data": None, "id": None}
        data_lines: list[str] = []
        for line in block.split("\n"):
            if line.startswith("event:"):
                ev["event"] = line[6:].strip()
            elif line.startswith("id:"):
                ev["id"] = line[3:].strip()
            elif line.startswith("data:"):
                data_lines.append(line[5:].strip())
        raw = "\n".join(data_lines)
        if not raw:
            continue
        try:
            ev["data"] = json.loads(raw)
        except json.JSONDecodeError:
            ev["data"] = raw
        events.append(ev)
    return events, buf


def is_suspended_agent_result(data: Any) -> bool:
    """与 FE isSuspendedAgentResult 对齐的精简判定。"""
    if not isinstance(data, dict) or data.get("type") != "AGENT_RESULT":
        return False
    msg = data.get("message") or {}
    meta = msg.get("metadata") or {}
    if meta.get("agentscope_generate_reason") == "TOOL_SUSPENDED":
        return True
    content = msg.get("content") or []
    if not isinstance(content, list):
        return False
    for b in content:
        if not isinstance(b, dict):
            continue
        if b.get("type") == "tool_result" and (b.get("metadata") or {}).get(
            "agentscope_suspended"
        ) is True:
            return True
        if b.get("type") == "tool_use" and b.get("name") == "request_user_input":
            return True
    return False


def main() -> int:
    print("== create session ==")
    created = http_json(
        "POST",
        "/api/v1/session/command/create",
        {"agentNum": AGT, "title": "e2e-hitl"},
    )
    print(json.dumps(created, ensure_ascii=False)[:400])
    sess = (created.get("data") or {}).get("num") or (created.get("data") or {}).get(
        "sessionNum"
    )
    if not sess:
        print("FAIL: no session", file=sys.stderr)
        return 2
    print("session", sess)

    body = {
        "agentNum": AGT,
        "session_num": sess,
        "target_version": TARGET_VERSION,
        "input": (
            "请立刻调用工具 request_user_input 向我收集信息。"
            "表单标题设为「E2E补全」，字段仅一个：name=city,label=城市,type=string,required=true。"
            "不要自己编造城市，必须等我提交后再回答。"
        ),
        "input_type": "text",
    }

    timeline: list[str] = []
    required: dict | None = None
    saw_suspend = False
    saw_accepted = False
    saw_post_resume = False
    finished_status: str | None = None
    abort_early = False
    lock = threading.Lock()
    stop_read = threading.Event()

    def reader(resp) -> None:
        nonlocal required, saw_suspend, saw_accepted, saw_post_resume, finished_status, abort_early
        buf = ""
        while not stop_read.is_set():
            chunk = resp.read(256)
            if not chunk:
                break
            buf += chunk.decode("utf-8", errors="replace")
            events, buf = parse_sse_chunk(buf)
            for ev in events:
                et = ev.get("event") or "message"
                data = ev.get("data")
                with lock:
                    if et == "user_input.required":
                        required = data if isinstance(data, dict) else None
                        timeline.append("user_input.required")
                        print("<< required", json.dumps(data, ensure_ascii=False)[:300])
                    elif et == "user_input.accepted":
                        saw_accepted = True
                        timeline.append("user_input.accepted")
                        print("<< accepted", data)
                    elif et == "run_finished":
                        finished_status = (data or {}).get("status") if isinstance(data, dict) else None
                        timeline.append(f"run_finished:{finished_status}")
                        print("<< run_finished", data)
                        stop_read.set()
                    elif et == "message" or et == "message":
                        if isinstance(data, dict) and data.get("type") == "AGENT_RESULT":
                            if is_suspended_agent_result(data):
                                saw_suspend = True
                                timeline.append("AGENT_RESULT:SUSPEND")
                                print("<< AGENT_RESULT SUSPEND (keep connection)")
                            else:
                                timeline.append("AGENT_RESULT:FINAL")
                                print("<< AGENT_RESULT FINAL")
                                if saw_accepted:
                                    saw_post_resume = True
                        elif isinstance(data, dict) and data.get("type") == "REASONING":
                            timeline.append("REASONING")
                            if saw_accepted:
                                saw_post_resume = True
                                print("<< REASONING after accepted")
                    else:
                        timeline.append(et)

    print("== open SSE invoke ==")
    req = urllib.request.Request(
        BASE + "/api/v1/debug-console/invoke",
        data=json.dumps(body).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Accept": "text/event-stream, application/json",
            "X-Workspace-Num": WS,
        },
    )
    resp = urllib.request.urlopen(req, timeout=300)
    ctype = resp.headers.get("Content-Type", "")
    if "text/event-stream" not in ctype:
        print("FAIL: not SSE", ctype, resp.read()[:500], file=sys.stderr)
        return 3

    t = threading.Thread(target=reader, args=(resp,), daemon=True)
    t.start()

    # wait required
    deadline = time.time() + 180
    while time.time() < deadline and required is None and not stop_read.is_set():
        time.sleep(0.2)
    if required is None:
        print("FAIL: no user_input.required", timeline, file=sys.stderr)
        stop_read.set()
        return 4
    if not saw_suspend:
        print("WARN: required without detecting suspend frame; timeline", timeline)

    # 挂起后稍等，确认连接未因 AGENT_RESULT 断开（还能继续读；respond 后应有帧）
    time.sleep(1.0)
    if stop_read.is_set() and not saw_accepted:
        abort_early = True
        print("FAIL: SSE ended before respond", timeline, file=sys.stderr)
        return 5

    request_id = required.get("requestId") or required.get("request_id")
    print("== respond submit ==", request_id)
    respond = http_json(
        "POST",
        "/api/v1/debug-console/user-input/respond",
        {
            "session_num": sess,
            "run_id": required.get("runId") or required.get("run_id"),
            "request_id": request_id,
            "reply_id": required.get("replyId") or required.get("reply_id"),
            "tool_call_id": required.get("toolCallId") or required.get("tool_call_id"),
            "action": "submit",
            "values": {"city": "深圳"},
        },
        timeout=120,
    )
    print("respond", respond)
    if respond.get("code") != 0:
        print("FAIL: respond", respond, file=sys.stderr)
        stop_read.set()
        return 6

    deadline = time.time() + 180
    while time.time() < deadline and finished_status is None:
        time.sleep(0.2)

    stop_read.set()
    t.join(timeout=5)
    try:
        resp.close()
    except Exception:
        pass

    print("== timeline ==")
    print(timeline)
    ok = (
        required is not None
        and saw_accepted
        and saw_post_resume
        and finished_status in ("DONE", "FAILED")
        and not abort_early
    )
    # FAILED 也算连通性通过，但标 WARN
    if finished_status == "FAILED":
        print("WARN: run_finished FAILED (connectivity OK, model/sandbox may have failed)")
    if ok and finished_status == "DONE":
        print("PASS: HITL SSE kept open and resumed to DONE")
        return 0
    if ok:
        print("PASS: HITL SSE kept open and resumed (status=%s)" % finished_status)
        return 0
    print(
        "FAIL: accepted=%s post_resume=%s finished=%s abort_early=%s"
        % (saw_accepted, saw_post_resume, finished_status, abort_early),
        file=sys.stderr,
    )
    return 7


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except urllib.error.HTTPError as e:
        print("HTTPError", e.code, e.read()[:500], file=sys.stderr)
        raise SystemExit(8)
    except Exception as e:
        print("ERROR", e, file=sys.stderr)
        raise SystemExit(9)
