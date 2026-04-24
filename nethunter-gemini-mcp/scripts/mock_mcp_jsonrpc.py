#!/usr/bin/env python3
"""
Minimal MCP-over-JSON-RPC HTTP server for local testing of the NetHunter Gemini MCP app.
Implements: initialize, tools/list, tools/call (one echo tool). Returns single JSON per POST.

  python3 scripts/mock_mcp_jsonrpc.py --port 3000

App settings URL:  http://127.0.0.1:3000/mcp

On a physical device, forward:  adb reverse tcp:3000 tcp:3000
In emulator, use:              http://10.0.2.2:3000/mcp  (host loopback)
"""
from __future__ import annotations

import argparse
import json
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Any


def rpc_response(req_id: int | None, result: Any | None, err: dict | None = None) -> dict:
    out: dict = {"jsonrpc": "2.0"}
    if req_id is not None:
        out["id"] = req_id
    if err is not None:
        out["error"] = err
    else:
        out["result"] = result
    return out


def handle_rpc(body: dict) -> dict:
    req_id = body.get("id")
    method = body.get("method")
    if method == "initialize":
        return rpc_response(
            req_id,
            {
                "protocolVersion": "2024-11-05",
                "serverInfo": {"name": "mock-mcp", "version": "0.1.0"},
                "capabilities": {"tools": {}},
            },
        )
    if method == "tools/list":
        return rpc_response(
            req_id,
            {
                "tools": [
                    {
                        "name": "echo",
                        "description": "Echo a string back (for testing).",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "message": {
                                    "type": "string",
                                    "description": "Text to echo",
                                }
                            },
                            "required": ["message"],
                        },
                    }
                ]
            },
        )
    if method == "tools/call":
        params = body.get("params") or {}
        name = params.get("name", "")
        args = params.get("arguments") or {}
        if name != "echo":
            return rpc_response(
                req_id,
                None,
                {
                    "code": -32601,
                    "message": f"unknown tool: {name!r}",
                },
            )
        msg = str(args.get("message", ""))
        text = f"echo: {msg}"
        return rpc_response(
            req_id,
            {
                "content": [{"type": "text", "text": text}],
            },
        )
    return rpc_response(
        req_id,
        None,
        {"code": -32601, "message": f"unknown method: {method!r}"},
    )


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format: str, *args) -> None:
        return

    def do_POST(self) -> None:
        path = self.path.split("?")[0].rstrip("/")
        if path not in ("/mcp", ""):
            self.send_error(404, "Use POST to /mcp")
            return
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            body = json.loads(raw.decode("utf-8"))
        except Exception:
            self._send(400, {"error": "invalid json"})
            return
        if not isinstance(body, dict):
            self._send(400, {"error": "json must be an object"})
            return
        out = handle_rpc(body)
        # Optional session id (app accepts and replays; mock ignores)
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Mcp-Session-Id", "mock-local")
        self.end_headers()
        self.wfile.write((json.dumps(out) + "\n").encode("utf-8"))

    def _send(self, code: int, obj: dict) -> None:
        b = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def do_GET(self) -> None:
        self._send(200, {"ok": True, "hint": "POST JSON-RPC to /mcp"})


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=3000)
    args = p.parse_args()
    server = HTTPServer((args.host, args.port), Handler)
    print(
        f"Mock MCP: http://{args.host}:{args.port}/mcp  (emulator: http://10.0.2.2:{args.port}/mcp )",
        flush=True,
    )
    server.serve_forever()


if __name__ == "__main__":
    main()
