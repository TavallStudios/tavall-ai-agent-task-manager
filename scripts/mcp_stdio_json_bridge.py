#!/usr/bin/env python3
"""Bridge framed MCP stdio to raw JSON-line stdio servers.

Some internal MCP launchers still speak newline-delimited JSON on stdio and
also emit startup logs to stdout. Codex expects framed MCP messages with
Content-Length headers. This bridge translates both directions and reroutes
non-JSON stdout lines to stderr so the protocol stream stays clean.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import threading
from typing import Optional


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cwd", required=True)
    parser.add_argument("jar_path")
    return parser.parse_args()


def start_child(cwd: str, jar_path: str) -> subprocess.Popen[bytes]:
    return subprocess.Popen(
        [
            "java",
            "-Dorg.springframework.boot.logging.LoggingSystem=none",
            "-Dspring.main.banner-mode=off",
            "-Dspring.output.ansi.enabled=never",
            "-jar",
            jar_path,
        ],
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

    body = bytes(buffer[header_end + 4 : frame_end])
    del buffer[:frame_end]
    return body


def forward_parent_to_child(child: subprocess.Popen[bytes]) -> None:
    buffer = bytearray()
    stdin = sys.stdin.buffer
    child_stdin = child.stdin
    if child_stdin is None:
        return

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


def forward_child_stdout(child: subprocess.Popen[bytes]) -> None:
    child_stdout = child.stdout
    if child_stdout is None:
        return

    line_buffer = bytearray()
    while True:
        chunk = os.read(child_stdout.fileno(), 8192)
        if not chunk:
            if line_buffer:
                flush_child_stdout_line(bytes(line_buffer))
            return

        line_buffer.extend(chunk)
        while True:
            newline_index = line_buffer.find(b"\n")
            if newline_index == -1:
                break
            line = bytes(line_buffer[:newline_index])
            del line_buffer[: newline_index + 1]
            flush_child_stdout_line(line)


def flush_child_stdout_line(line: bytes) -> None:
    stripped = line.strip()
    if not stripped:
        return

    if is_json_rpc_message(stripped):
        sys.stdout.buffer.write(frame_message(stripped))
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
    child = start_child(args.cwd, args.jar_path)

    stdin_thread = threading.Thread(target=forward_parent_to_child, args=(child,), daemon=True)
    stderr_thread = threading.Thread(target=forward_child_stderr, args=(child,), daemon=True)
    stdin_thread.start()
    stderr_thread.start()

    try:
        forward_child_stdout(child)
    finally:
        try:
            child.terminate()
        except OSError:
            pass

    return child.wait()


if __name__ == "__main__":
    raise SystemExit(main())
