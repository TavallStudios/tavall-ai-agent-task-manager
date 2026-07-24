#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import sys

from _atm_plugin_common import (
    acquire_mcp_stdio_lock,
    ensure_app_distribution,
    fail,
    resolve_repo_root,
)


def parse_args() -> tuple[argparse.Namespace, list[str]]:
  parser = argparse.ArgumentParser(
      description="Start the AgentTaskManager MCP server over stdio."
  )
  parser.add_argument(
      "--print-repo-root",
      action="store_true",
      help="Print the resolved repo root and exit.",
  )
  return parser.parse_known_args()


def main() -> int:
  args, remainder = parse_args()

  try:
    repo_root = resolve_repo_root()
  except RuntimeError as error:
    return fail(str(error))

  if args.print_repo_root:
    print(repo_root)
    return 0

  try:
    distribution_path = ensure_app_distribution(repo_root)
    acquire_mcp_stdio_lock(repo_root, distribution_path)
  except RuntimeError as error:
    return fail(str(error))
  except Exception as error:
    return fail(f"Failed to prepare the ATM MCP runtime: {error}")

  bridge_path = repo_root / "scripts" / "mcp_stdio_json_bridge.py"
  protocol = os.environ.get("TAVALL_AI_STDIO_PROTOCOL", "auto")
  if not protocol.strip():
    protocol = "auto"
  disable_db = os.environ.get("TAVALL_AI_STDIO_DISABLE_DB", "")

  command = [
      sys.executable,
      str(bridge_path),
      "--cwd",
      str(repo_root),
      "--distribution-path",
      str(distribution_path),
      "--protocol",
      protocol,
  ]
  if disable_db.strip():
    command.append("--disable-db")
  if remainder:
    command.extend(["--", *remainder])

  os.execvp(command[0], command)
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
