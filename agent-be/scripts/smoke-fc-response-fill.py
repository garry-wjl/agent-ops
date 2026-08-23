#!/usr/bin/env python3
"""Fill roundtrip: create → FE testFunctionCall → infer response schema → update → FE detail."""
from __future__ import annotations

import json
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8081"
FE = "http://127.0.0.1:8001"
WS = "WS-1997fd034709"
USER = "alice.zhang"
HDR = {
    "Content-Type": "application/json",
    "X-User-Id": USER,
    "X-Workspace-Num": WS,
}


def call(url: str, payload: dict | None = None) -> dict:
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(url, data=data, headers=HDR, method="GET" if payload is None else "POST")
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode())


def infer(v):
    if v is None:
        return {"type": "string"}
    if isinstance(v, bool):
        return {"type": "boolean"}
    if isinstance(v, int) and not isinstance(v, bool):
        return {"type": "integer"}
    if isinstance(v, float):
        return {"type": "number"}
    if isinstance(v, str):
        return {"type": "string"}
    if isinstance(v, list):
        item = infer(v[0]) if v else {"type": "string"}
        return {"type": "array", "items": item}
    if isinstance(v, dict):
        return {"type": "object", "properties": {k: infer(x) for k, x in v.items()}}
    return {"type": "string"}


def main() -> int:
    ts = int(time.time())
    name = f"smoke-fc-fill-{ts}"
    create = call(
        f"{BASE}/api/v1/tool/command/create",
        {
            "name": name,
            "description": "fill roundtrip",
            "type": "FUNCTION_CALL",
            "creationMode": "MANUAL",
            "baseUrl": "https://httpbin.org",
            "endpoints": [
                {
                    "method": "POST",
                    "path": "/post",
                    "description": "echo",
                    "queryParams": [],
                    "pathParams": [],
                    "headers": [],
                    "requestBodySchema": {
                        "type": "object",
                        "properties": {"name": {"type": "string", "default": "alice"}},
                    },
                    "requestBodyRequired": True,
                }
            ],
        },
    )
    assert create["code"] == 0, create
    num = create["data"]["num"]
    print(f"PASS create {num}")

    test = call(
        f"{FE}/api/v1/tool/command/testFunctionCall",
        {
            "baseUrl": "https://httpbin.org",
            "endpoint": {
                "method": "POST",
                "path": "/post",
                "description": "echo",
                "requestBodySchema": {
                    "type": "object",
                    "properties": {"name": {"type": "string", "default": "alice"}},
                },
            },
            "body": {"name": "alice"},
        },
    )
    assert test["code"] == 0, test
    data = test["data"]
    assert data["httpStatus"] == 200 and data.get("responseSample"), data
    sample = json.loads(data["responseSample"])
    assert sample.get("json", {}).get("name") == "alice", sample
    schema = infer(sample)
    print("PASS FE testFunctionCall + sample")

    upd = call(
        f"{BASE}/api/v1/tool/command/update",
        {
            "num": num,
            "name": name,
            "description": "fill roundtrip",
            "baseUrl": "https://httpbin.org",
            "endpoints": [
                {
                    "method": "POST",
                    "path": "/post",
                    "description": "echo",
                    "queryParams": [],
                    "pathParams": [],
                    "headers": [],
                    "requestBodySchema": {
                        "type": "object",
                        "properties": {"name": {"type": "string", "default": "alice"}},
                    },
                    "requestBodyRequired": True,
                    "responseBodySchema": schema,
                }
            ],
        },
    )
    assert upd["code"] == 0, upd
    print("PASS update with filled responseBodySchema")

    detail = call(f"{FE}/api/v1/tool/query/detail?num={num}")
    assert detail["code"] == 0, detail
    tool = detail["data"].get("tool") or detail["data"]
    props = tool["endpoints"][0]["responseBodySchema"]["properties"]
    for k in ("json", "url", "headers"):
        assert k in props, props.keys()
    print(f"PASS FE detail retained fill keys ({len(props)} top-level)")

    t404 = call(
        f"{BASE}/api/v1/tool/command/testFunctionCall",
        {
            "baseUrl": "https://httpbin.org",
            "endpoint": {"method": "GET", "path": "/status/404", "description": "n"},
        },
    )
    assert t404["code"] == 0 and t404["data"]["httpStatus"] == 404
    assert not t404["data"].get("responseSample")
    print("PASS no responseSample on HTTP 404")

    call(f"{BASE}/api/v1/tool/command/deleteDraft", {"num": num})
    print("PASS cleanup")
    print("==== FILL ROUNDTRIP ALL PASS ====")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as e:
        print("FAIL", e, file=sys.stderr)
        raise SystemExit(1)
