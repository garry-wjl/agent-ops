#!/usr/bin/env python3
"""
将 Agent 快照中的「工具组绑定」(toolNums / 仅 toolNum 的 toolRefs)
可选展开为「具体工具绑定」(toolRefs 带 itemKind)。

说明：产品支持「整组 + 具体」双模式并存；本脚本为可选迁移，
不是兼容所必需——运行时无 itemKind 仍按整组挂载。

兼容策略（若执行展开）：
- FUNCTION_CALL：按 tool.endpoints 或 endpoint_meta.summaries 展开为 FC_ENDPOINT
- MCP REMOTE：本脚本不连远端；保留整组语义（仅 toolNum、无 itemKind）
- MCP API_PACKAGE + EXISTING_API：展开来源 FC 的端点

用法（在能访问业务库的环境）：
  export MYSQL_HOST=... MYSQL_PORT=3306 MYSQL_USER=... MYSQL_PASSWORD=... MYSQL_DB=rd_agent
  python3 agent-be/scripts/migrate-expand-tool-bindings.py [--dry-run]

依赖：pip install pymysql
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from typing import Any

try:
    import pymysql
except ImportError:
    print("需要 pymysql：pip install pymysql", file=sys.stderr)
    sys.exit(1)


def env(name: str, default: str | None = None) -> str:
    v = os.environ.get(name, default)
    if not v:
        raise SystemExit(f"缺少环境变量 {name}")
    return v


def load_tools(cur) -> dict[str, dict[str, Any]]:
    cur.execute(
        "SELECT num, type, creation_mode, package_mode, source_fc_tool_num, "
        "endpoints, endpoint_meta FROM tool WHERE deleted = 0 OR deleted IS NULL"
    )
    # MySQL 可能没有 deleted 列 — 回退
    try:
        rows = cur.fetchall()
    except Exception:
        cur.execute(
            "SELECT num, type, creation_mode, package_mode, source_fc_tool_num, "
            "endpoints, endpoint_meta FROM tool"
        )
        rows = cur.fetchall()
    out: dict[str, dict[str, Any]] = {}
    for r in rows:
        out[r["num"]] = r
    return out


def parse_json(val: Any) -> Any:
    if val is None:
        return None
    if isinstance(val, (dict, list)):
        return val
    if isinstance(val, (bytes, bytearray)):
        val = val.decode("utf-8")
    if isinstance(val, str):
        if not val.strip():
            return None
        return json.loads(val)
    return val


def expand_fc_endpoints(tool: dict[str, Any], tool_num: str) -> list[dict[str, Any]]:
    refs: list[dict[str, Any]] = []
    endpoints = parse_json(tool.get("endpoints")) or []
    if isinstance(endpoints, list) and endpoints:
        for ep in endpoints:
            if not isinstance(ep, dict):
                continue
            path = ep.get("path") or ""
            method = (ep.get("method") or "GET").upper()
            if not path:
                continue
            refs.append(
                {
                    "toolNum": tool_num,
                    "itemKind": "FC_ENDPOINT",
                    "method": method,
                    "path": path,
                }
            )
        return refs
    meta = parse_json(tool.get("endpoint_meta")) or {}
    summaries = meta.get("summaries") if isinstance(meta, dict) else None
    if isinstance(summaries, list):
        for s in summaries:
            if not isinstance(s, dict):
                continue
            path = s.get("path") or ""
            method = (s.get("method") or "GET").upper()
            if not path:
                continue
            refs.append(
                {
                    "toolNum": tool_num,
                    "itemKind": "FC_ENDPOINT",
                    "method": method,
                    "path": path,
                }
            )
    return refs


def expand_tool_num(tool_num: str, tools: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    tool = tools.get(tool_num)
    if not tool:
        return [{"toolNum": tool_num}]
    t = (tool.get("type") or "").upper()
    mode = (tool.get("creation_mode") or "").upper()
    if t == "FUNCTION_CALL":
        refs = expand_fc_endpoints(tool, tool_num)
        return refs or [{"toolNum": tool_num}]
    if t == "MCP" and mode == "API_PACKAGE":
        src = tool.get("source_fc_tool_num")
        if src and tools.get(src):
            refs = expand_fc_endpoints(tools[src], src)
            # 绑定仍挂在 MCP 资产 num 下？运行时 EXISTING_API 用 source 构建。
            # 产品上挂载的是 MCP 资产；展开端点应使用 source 的 method/path，
            # toolNum 保持 MCP 资产编号以便复用数统计。
            # 但运行时 matchesFcBinding 按 source FC 的 endpoint 匹配，
            # AgentRunner 对 MCP API_PACKAGE 走 buildTools(toolDTO) 而非 MCP client。
            # buildTools 对 EXISTING_API 委托 source — endpoint 来自 source。
            # 因此 toolNum 应为 MCP 资产，method/path 来自 source endpoints。
            out = []
            for r in refs:
                out.append(
                    {
                        "toolNum": tool_num,
                        "itemKind": "FC_ENDPOINT",
                        "method": r["method"],
                        "path": r["path"],
                    }
                )
            return out or [{"toolNum": tool_num}]
    # MCP REMOTE：脚本侧无法 listTools，保留整组
    return [{"toolNum": tool_num}]


def migrate_snapshot(
    snap: dict[str, Any], tools: dict[str, dict[str, Any]]
) -> tuple[dict[str, Any], bool]:
    changed = False
    refs = snap.get("toolRefs")
    nums = snap.get("toolNums") or []

    # 已有具体绑定则跳过
    if isinstance(refs, list) and refs:
        if any(isinstance(r, dict) and r.get("itemKind") for r in refs):
            # 同步 toolNums
            parents = sorted(
                {
                    r.get("toolNum")
                    for r in refs
                    if isinstance(r, dict) and r.get("toolNum")
                }
            )
            if snap.get("toolNums") != parents:
                snap["toolNums"] = parents
                changed = True
            return snap, changed
        # 仅有整组 toolRefs
        source_nums = [
            r.get("toolNum")
            for r in refs
            if isinstance(r, dict) and r.get("toolNum")
        ]
    else:
        source_nums = list(nums)

    if not source_nums:
        return snap, False

    new_refs: list[dict[str, Any]] = []
    seen: set[str] = set()
    for num in source_nums:
        for r in expand_tool_num(num, tools):
            key = (
                f"{r.get('toolNum')}|{r.get('itemKind')}|{r.get('method')}|{r.get('path')}|{r.get('mcpToolName')}"
            )
            if key in seen:
                continue
            seen.add(key)
            new_refs.append(r)

    snap["toolRefs"] = new_refs
    snap["toolNums"] = sorted(
        {r["toolNum"] for r in new_refs if r.get("toolNum")}
    )
    return snap, True


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    conn = pymysql.connect(
        host=env("MYSQL_HOST", "127.0.0.1"),
        port=int(os.environ.get("MYSQL_PORT", "3306")),
        user=env("MYSQL_USER", "root"),
        password=env("MYSQL_PASSWORD", ""),
        database=env("MYSQL_DB", "rd_agent"),
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=False,
    )
    updated = 0
    try:
        with conn.cursor() as cur:
            tools = load_tools(cur)
            # agent 主表镜像
            cur.execute(
                "SELECT id, num, config_snapshot FROM agent WHERE config_snapshot IS NOT NULL"
            )
            agents = cur.fetchall()
            for a in agents:
                snap = parse_json(a["config_snapshot"])
                if not isinstance(snap, dict):
                    continue
                new_snap, changed = migrate_snapshot(snap, tools)
                if not changed:
                    continue
                updated += 1
                print(f"[agent] {a['num']} -> {len(new_snap.get('toolRefs') or [])} refs")
                if not args.dry_run:
                    cur.execute(
                        "UPDATE agent SET config_snapshot=%s WHERE id=%s",
                        (json.dumps(new_snap, ensure_ascii=False), a["id"]),
                    )
            # agent_version
            cur.execute(
                "SELECT id, agent_num, version, config_snapshot FROM agent_version "
                "WHERE config_snapshot IS NOT NULL"
            )
            versions = cur.fetchall()
            for v in versions:
                snap = parse_json(v["config_snapshot"])
                if not isinstance(snap, dict):
                    continue
                new_snap, changed = migrate_snapshot(snap, tools)
                if not changed:
                    continue
                updated += 1
                print(
                    f"[version] {v['agent_num']}@{v.get('version')} -> "
                    f"{len(new_snap.get('toolRefs') or [])} refs"
                )
                if not args.dry_run:
                    cur.execute(
                        "UPDATE agent_version SET config_snapshot=%s WHERE id=%s",
                        (json.dumps(new_snap, ensure_ascii=False), v["id"]),
                    )
        if args.dry_run:
            print(f"dry-run 完成，将更新 {updated} 行")
            conn.rollback()
        else:
            conn.commit()
            print(f"已更新 {updated} 行")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
