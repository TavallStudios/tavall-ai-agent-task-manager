#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os

from _atm_plugin_common import ensure_app_jar, fail, resolve_java_command, resolve_repo_root


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
    jar_path = ensure_app_jar(repo_root)
    java_command = resolve_java_command()
  except RuntimeError as error:
    return fail(str(error))
  except Exception as error:
    return fail(f"Failed to prepare the ATM MCP runtime: {error}")

  command = [java_command, "-jar", str(jar_path), "serve-mcp-stdio", *remainder]
  os.execvp(command[0], command)
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
