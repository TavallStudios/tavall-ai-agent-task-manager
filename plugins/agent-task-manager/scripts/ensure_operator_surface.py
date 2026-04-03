#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import subprocess
import time
from pathlib import Path

from _atm_plugin_common import (
    ensure_app_jar,
    fail,
    http_reachable,
    quoted_command,
    resolve_java_command,
    resolve_repo_root,
)


def parse_args() -> argparse.Namespace:
  parser = argparse.ArgumentParser(
      description="Ensure the local AgentTaskManager HTTP operator surface is available."
  )
  parser.add_argument("--base-url", default="http://127.0.0.1:9000")
  parser.add_argument("--probe-path", default="/login")
  parser.add_argument("--port", type=int, default=9000)
  parser.add_argument("--password", default="local-dev-password")
  parser.add_argument("--timeout-seconds", type=int, default=20)
  parser.add_argument("--spring-log-level", default="WARN")
  parser.add_argument("--app-log-level", default="INFO")
  parser.add_argument(
      "--check-only",
      action="store_true",
      help="Only probe the operator surface. Do not attempt to start it.",
  )
  parser.add_argument(
      "--print-repo-root",
      action="store_true",
      help="Print the resolved repo root and exit.",
  )
  return parser.parse_args()


def probe_url(base_url: str, probe_path: str) -> str:
  return f"{base_url.rstrip('/')}{probe_path}"


def log_paths(repo_root: Path, port: int) -> tuple[Path, Path]:
  log_dir = repo_root / ".tmp" / "plugin-operator-surface" / str(port)
  log_dir.mkdir(parents=True, exist_ok=True)
  return log_dir / "stdout.log", log_dir / "stderr.log"


def build_start_command(
    java_command: str,
    jar_path: Path,
    port: int,
    password: str,
    spring_log_level: str,
    app_log_level: str,
) -> list[str]:
  return [
      java_command,
      "-jar",
      str(jar_path),
      f"--server.port={port}",
      f"--app.security.password={password}",
      "--app.codex-client-platform.enabled=true",
      "--app.security.proxy-auth-enabled=false",
      "--app.orchestration.autonomy-enabled=false",
      f"--logging.level.org.springframework={spring_log_level}",
      f"--logging.level.com.agenttaskmanager={app_log_level}",
  ]


def main() -> int:
  args = parse_args()

  try:
    repo_root = resolve_repo_root()
  except RuntimeError as error:
    return fail(str(error))

  if args.print_repo_root:
    print(repo_root)
    return 0

  target_url = probe_url(args.base_url, args.probe_path)
  if http_reachable(target_url):
    print(f"AgentTaskManager operator surface is already reachable at {target_url}.")
    return 0

  if args.check_only:
    return fail(f"AgentTaskManager operator surface is not reachable at {target_url}.")

  try:
    jar_path = ensure_app_jar(repo_root)
    java_command = resolve_java_command()
  except RuntimeError as error:
    return fail(str(error))
  except Exception as error:
    return fail(f"Failed to prepare the AgentTaskManager HTTP runtime: {error}")

  stdout_path, stderr_path = log_paths(repo_root, args.port)
  stdout_handle = stdout_path.open("w", encoding="utf-8")
  stderr_handle = stderr_path.open("w", encoding="utf-8")

  command = build_start_command(
      java_command,
      jar_path,
      args.port,
      args.password,
      args.spring_log_level,
      args.app_log_level,
  )

  popen_kwargs = {
      "cwd": repo_root,
      "stdout": stdout_handle,
      "stderr": stderr_handle,
      "stdin": subprocess.DEVNULL,
      "start_new_session": True,
  }
  if os.name == "nt":
    popen_kwargs["creationflags"] = subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.DETACHED_PROCESS

  try:
    process = subprocess.Popen(command, **popen_kwargs)
  except Exception as error:
    stdout_handle.close()
    stderr_handle.close()
    return fail(f"Failed to start the AgentTaskManager operator surface: {error}")

  print(f"Starting AgentTaskManager operator surface with: {quoted_command(command)}")
  print(f"stdout: {stdout_path}")
  print(f"stderr: {stderr_path}")
  print(f"pid: {process.pid}")

  deadline = time.time() + args.timeout_seconds
  while time.time() < deadline:
    if http_reachable(target_url):
      print(f"AgentTaskManager operator surface is reachable at {target_url}.")
      return 0
    if process.poll() is not None:
      return fail(
          "AgentTaskManager operator surface exited before becoming reachable. "
          f"See {stderr_path}."
      )
    time.sleep(2)

  return fail(
      "AgentTaskManager operator surface did not become reachable within "
      f"{args.timeout_seconds}s. See {stdout_path} and {stderr_path}."
  )


if __name__ == "__main__":
  raise SystemExit(main())
