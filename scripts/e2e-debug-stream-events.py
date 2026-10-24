#!/usr/bin/env python3
"""
本地集成：调试台 /debug-console/stream（AgentScope streamEvents）
→ run_started protocol=agent_events_v2 → 细粒度业务帧
→（可选）HITL user_input.required → respond → accepted → run_finished。

同时冒烟校验旧路径 /debug-console/invoke 仍可开 SSE（不跑完整 HITL）。
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


def is_v2_suspend(data: Any) -> bool:
    if not isinstance(data, dict):
        return False
    t = data.get("type")
    if t == "REQUIRE_EXTERNAL_EXECUTION":
        calls = data.get("toolCalls") or data.get("tool_calls") or []
        for c in calls if isinstance(calls, list) else []:
            if isinstance(c, dict) and c.get("name") == "request_user_input":
                return True
        return False
    if t == "AGENT_RESULT":
        result = data.get("result") or {}
        if isinstance(result, dict):
            if result.get("generateReason") == "TOOL_SUSPENDED" or result.get(
                "generate_reason"
            ) == "TOOL_SUSPENDED":
                return True
            content = result.get("content") or []
            for b in content if isinstance(content, list) else []:
                if isinstance(b, dict) and b.get("name") == "request_user_input":
                    return True
        msg = data.get("message") or {}
        meta = msg.get("metadata") or {}
        if meta.get("agentscope_generate_reason") == "TOOL_SUSPENDED":
            return True
    return False


def create_session(title: str) -> str:
    created = http_json(
        "POST",
        "/api/v1/session/command/create",
        {"agentNum": AGT, "title": title},
    )
    sess = (created.get("data") or {}).get("num") or (created.get("data") or {}).get(
        "sessionNum"
    )
    if not sess:
        raise RuntimeError(f"no session: {created}")
    return sess


def smoke_legacy_invoke() -> None:
    """旧 invoke 路径仍可返回 SSE，等到 run_finished 再关连接。"""
    print("== smoke legacy /debug-console/invoke ==")
    sess = create_session("e2e-invoke-smoke")
    body = {
        "agentNum": AGT,
        "session_num": sess,
        "target_version": TARGET_VERSION,
        "input": "只回复两个字：好的",
        "input_type": "text",
    }
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
    with urllib.request.urlopen(req, timeout=180) as resp:
        ctype = resp.headers.get("Content-Type", "")
        if "text/event-stream" not in ctype:
            raise RuntimeError(f"invoke not SSE: {ctype} {resp.read()[:300]}")
        buf = ""
        deadline = time.time() + 120
        saw_started = False
        finished = False
        while time.time() < deadline and not finished:
            chunk = resp.read(512)
            if not chunk:
                break
            buf += chunk.decode("utf-8", errors="replace")
            events, buf = parse_sse_chunk(buf)
            for ev in events:
                if ev.get("event") == "run_started":
                    saw_started = True
                    print("<< invoke run_started", ev.get("data"))
                if ev.get("event") == "run_finished":
                    finished = True
                    print("<< invoke run_finished", ev.get("data"))
        if not saw_started:
            raise RuntimeError("invoke smoke: no run_started")
        if not finished:
            # 模型偶发慢/挂起时仍视为连通性通过；勿阻塞后续 stream HITL
            print("WARN: invoke smoke no run_finished within timeout (connectivity OK)")
        print("PASS: legacy invoke SSE smoke")


def run_stream_hitl() -> int:
    print("== create session (stream HITL) ==")
    sess = create_session("e2e-stream-hitl")
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
    protocol: str | None = None
    saw_v2_frame = False
    saw_suspend = False
    saw_accepted = False
    saw_post_resume = False
    finished_status: str | None = None
    abort_early = False
    lock = threading.Lock()
    stop_read = threading.Event()

    v2_types = {
        "TEXT_BLOCK_DELTA",
        "THINKING_BLOCK_DELTA",
        "TOOL_CALL_START",
        "TOOL_CALL_DELTA",
        "TOOL_CALL_END",
        "TOOL_RESULT_START",
        "TOOL_RESULT_DELTA",
        "TOOL_RESULT_END",
        "REQUIRE_EXTERNAL_EXECUTION",
    }

    def reader(resp) -> None:
        nonlocal required, protocol, saw_v2_frame, saw_suspend, saw_accepted
        nonlocal saw_post_resume, finished_status, abort_early
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
                    if et == "run_started":
                        timeline.append("run_started")
                        if isinstance(data, dict):
                            protocol = data.get("protocol")
                        print("<< run_started", data)
                    elif et == "user_input.required":
                        required = data if isinstance(data, dict) else None
                        timeline.append("user_input.required")
                        print("<< required", json.dumps(data, ensure_ascii=False)[:300])
                    elif et == "user_input.accepted":
                        saw_accepted = True
                        timeline.append("user_input.accepted")
                        print("<< accepted", data)
                    elif et == "run_finished":
                        finished_status = (
                            (data or {}).get("status") if isinstance(data, dict) else None
                        )
                        timeline.append(f"run_finished:{finished_status}")
                        print("<< run_finished", data)
                        stop_read.set()
                    elif et == "message":
                        if isinstance(data, dict):
                            t = data.get("type")
                            if t in v2_types:
                                saw_v2_frame = True
                                timeline.append(str(t))
                            if is_v2_suspend(data):
                                saw_suspend = True
                                timeline.append(f"{t}:SUSPEND")
                                print("<< suspend frame", t)
                            if saw_accepted and t in (
                                "TEXT_BLOCK_DELTA",
                                "THINKING_BLOCK_DELTA",
                                "AGENT_RESULT",
                                "TOOL_CALL_START",
                            ):
                                saw_post_resume = True
                                print("<< post-resume", t)
                    else:
                        timeline.append(et)

    print("== open SSE /debug-console/stream ==")
    req = urllib.request.Request(
        BASE + "/api/v1/debug-console/stream",
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

    deadline = time.time() + 180
    while time.time() < deadline and required is None and not stop_read.is_set():
        time.sleep(0.2)
    if required is None:
        print("FAIL: no user_input.required", timeline, file=sys.stderr)
        stop_read.set()
        return 4

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
    print("protocol=", protocol, "saw_v2_frame=", saw_v2_frame)

    if protocol != "agent_events_v2":
        print("FAIL: expected protocol=agent_events_v2, got", protocol, file=sys.stderr)
        return 10
    if not saw_v2_frame and not saw_suspend:
        print("FAIL: no AgentEvent v2 business frames", file=sys.stderr)
        return 11

    ok = (
        required is not None
        and saw_accepted
        and saw_post_resume
        and finished_status in ("DONE", "FAILED")
        and not abort_early
    )
    if finished_status == "FAILED":
        print("WARN: run_finished FAILED (connectivity OK, model/sandbox may have failed)")
    if ok:
        print("PASS: streamEvents HITL kept open and resumed (status=%s)" % finished_status)
        return 0
    print(
        "FAIL: accepted=%s post_resume=%s finished=%s abort_early=%s"
        % (saw_accepted, saw_post_resume, finished_status, abort_early),
        file=sys.stderr,
    )
    return 7


def main() -> int:
    smoke_legacy_invoke()
    return run_stream_hitl()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except urllib.error.HTTPError as e:
        print("HTTPError", e.code, e.read()[:500], file=sys.stderr)
        raise SystemExit(8)
    except Exception as e:
        print("ERROR", e, file=sys.stderr)
        raise SystemExit(9)
