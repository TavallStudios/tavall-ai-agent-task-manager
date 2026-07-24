#!/usr/bin/env python3
"""Bridge framed MCP stdio to raw JSON-line stdio servers.

This bridge auto-detects whether the parent speaks Content-Length framed MCP
messages or newline-delimited JSON. It translates both directions and reroutes
non-JSON stdout lines to stderr so the protocol stream stays clean.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import threading
from pathlib import Path
from typing import Optional


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cwd", required=True)
    parser.add_argument("--distribution-path", required=True)
    parser.add_argument(
        "--protocol",
        choices=["auto", "content-length", "line"],
        default=None,
        help="Force stdio framing (default: auto).",
    )
    parser.add_argument(
        "--disable-db",
        action="store_true",
        help="Disable embedded Postgres for stdio runs.",
    )
    parser.add_argument("child_args", nargs=argparse.REMAINDER)
    return parser.parse_args()


def parse_bool_env(value: str | None) -> bool:
    if value is None:
        return False
    return value.strip().lower() in {"1", "true", "yes", "on"}


def normalize_protocol(value: str | None) -> str:
    if not value:
        return "auto"
    normalized = value.strip().lower()
    if normalized in {"contentlength", "content_length"}:
        return "content-length"
    if normalized in {"line", "lines", "json", "json-line"}:
        return "line"
    if normalized == "content-length":
        return normalized
    return "auto"


def start_child(cwd: str, distribution_path: str, disable_db: bool, child_args: list[str]) -> subprocess.Popen[bytes]:
    classpath = os.pathsep.join(
        [
            str(Path(distribution_path) / "application.jar"),
            str(Path(distribution_path) / "libs" / "*"),
        ]
    )
    args = [
        "java",
        "--enable-preview",
        "-Dorg.springframework.boot.logging.LoggingSystem=none",
        "-Dspring.main.banner-mode=off",
        "-Dspring.output.ansi.enabled=never",
        "-cp",
        classpath,
        "org.tavall.ai.app.AgentTaskManagerLauncher",
        "serve-mcp-stdio",
    ]
    if disable_db:
        args.append("--tavall.ai.embedded-postgres.enabled=false")
        args.append("--spring.sql.init.mode=never")
    if child_args:
        if child_args and child_args[0] == "--":
            child_args = child_args[1:]
        args.extend(child_args)

    return subprocess.Popen(
        args,
        cwd=cwd,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        bufsize=0,
    )


def frame_message(body: bytes) -> bytes:
    header = f"Content-Length: {len(body)}\r\n\r\n".encode("ascii")
    return header + body


def try_extract_frame(buffer: bytearray) -> Optional[bytes]:
    header_end = buffer.find(b"\r\n\r\n")
    if header_end == -1:
        return None

    headers = buffer[:header_end].decode("ascii", errors="ignore").split("\r\n")
    content_length = None
    for line in headers:
        if line.lower().startswith("content-length:"):
            content_length = int(line.split(":", 1)[1].strip())
            break

    if content_length is None:
        raise ValueError(f"Missing Content-Length header in {headers!r}")

    frame_end = header_end + 4 + content_length
    if len(buffer) < frame_end:
        return None

    body = bytes(buffer[header_end + 4: frame_end])
    del buffer[:frame_end]
    return body


def detect_protocol(buffer: bytearray) -> Optional[str]:
    stripped = bytes(buffer).lstrip()
    if not stripped:
        return None
    lowered = stripped.lower()
    if lowered.startswith(b"content-length:"):
        return "content-length"
    if stripped[:1] in {b"{", b"["}:
        return "line"
    return None


def forward_parent_to_child(
    child: subprocess.Popen[bytes],
    protocol_holder: dict[str, Optional[str]],
    protocol_ready: threading.Event,
    configured_protocol: str,
) -> None:
    buffer = bytearray()
    stdin = sys.stdin.buffer
    child_stdin = child.stdin
    if child_stdin is None:
        return

    protocol = None if configured_protocol == "auto" else configured_protocol

    try:
        while True:
            chunk = os.read(stdin.fileno(), 8192)
            if not chunk:
                try:
                    child_stdin.close()
                except OSError:
                    pass
                return

            buffer.extend(chunk)
            if protocol is None:
                protocol = detect_protocol(buffer)
                if protocol is None and len(buffer) < 8192:
                    continue
                if protocol is None:
                    protocol = "line"
                protocol_holder["value"] = protocol
                protocol_ready.set()

            if protocol == "content-length":
                while True:
                    try:
                        message = try_extract_frame(buffer)
                    except ValueError as error:
                        sys.stderr.write(f"[mcp-bridge] {error}\n")
                        sys.stderr.flush()
                        buffer.clear()
                        break

                    if message is None:
                        break

                    child_stdin.write(message + b"\n")
                    child_stdin.flush()
            else:
                while True:
                    newline_index = buffer.find(b"\n")
                    if newline_index == -1:
                        break
                    line = bytes(buffer[:newline_index]).rstrip(b"\r")
                    del buffer[: newline_index + 1]
                    if not line:
                        continue
                    child_stdin.write(line + b"\n")
                    child_stdin.flush()
    except BrokenPipeError:
        return


def forward_child_stderr(child: subprocess.Popen[bytes]) -> None:
    child_stderr = child.stderr
    if child_stderr is None:
        return

    while True:
        chunk = os.read(child_stderr.fileno(), 8192)
        if not chunk:
            return
        sys.stderr.buffer.write(chunk)
        sys.stderr.buffer.flush()


def forward_child_stdout(
    child: subprocess.Popen[bytes],
    protocol_holder: dict[str, Optional[str]],
    protocol_ready: threading.Event,
) -> None:
    child_stdout = child.stdout
    if child_stdout is None:
        return

    protocol_ready.wait()
    protocol = protocol_holder.get("value") or "line"

    line_buffer = bytearray()
    while True:
        chunk = os.read(child_stdout.fileno(), 8192)
        if not chunk:
            if line_buffer:
                flush_child_stdout_line(bytes(line_buffer), protocol)
            return

        line_buffer.extend(chunk)
        while True:
            newline_index = line_buffer.find(b"\n")
            if newline_index == -1:
                break
            line = bytes(line_buffer[:newline_index])
            del line_buffer[: newline_index + 1]
            flush_child_stdout_line(line, protocol)


def flush_child_stdout_line(line: bytes, protocol: str) -> None:
    stripped = line.strip()
    if not stripped:
        return

    if is_json_rpc_message(stripped):
        if protocol == "content-length":
            sys.stdout.buffer.write(frame_message(stripped))
        else:
            sys.stdout.buffer.write(stripped + b"\n")
        sys.stdout.buffer.flush()
        return

    sys.stderr.buffer.write(line + b"\n")
    sys.stderr.buffer.flush()


def is_json_rpc_message(payload: bytes) -> bool:
    if payload[:1] not in {b"{", b"["}:
        return False

    try:
        message = json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return False

    if isinstance(message, dict):
        return message.get("jsonrpc") == "2.0"
    return False


def main() -> int:
    args = parse_args()
    protocol = normalize_protocol(args.protocol or os.environ.get("TAVALL_AI_STDIO_PROTOCOL"))
    disable_db = args.disable_db or parse_bool_env(os.environ.get("TAVALL_AI_STDIO_DISABLE_DB"))

    protocol_holder: dict[str, Optional[str]] = {"value": None if protocol == "auto" else protocol}
    protocol_ready = threading.Event()
    if protocol != "auto":
        protocol_ready.set()

    child = start_child(args.cwd, args.distribution_path, disable_db, args.child_args)

    stdin_thread = threading.Thread(
        target=forward_parent_to_child,
        args=(child, protocol_holder, protocol_ready, protocol),
        daemon=True,
    )
    stderr_thread = threading.Thread(target=forward_child_stderr, args=(child,), daemon=True)
    stdout_thread = threading.Thread(
        target=forward_child_stdout,
        args=(child, protocol_holder, protocol_ready),
        daemon=True,
    )

    stdin_thread.start()
    stderr_thread.start()
    stdout_thread.start()

    stdout_thread.join()
    try:
        child.terminate()
    except OSError:
        pass

    return child.wait()


if __name__ == "__main__":
    raise SystemExit(main())
