from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


def plugin_root() -> Path:
  return Path(__file__).resolve().parent.parent


def has_repo_layout(candidate: Path) -> bool:
  has_app = (candidate / "tavall-ai-app").is_dir()
  has_core = (candidate / "tavall-ai-core").is_dir()
  has_legacy_root = (candidate / "agent-task-manager").is_dir()
  return (candidate / "pom.xml").is_file() and has_app and (has_core or has_legacy_root)


def candidate_repo_roots() -> list[Path]:
  home = Path.home()
  script_root = plugin_root()
  candidates: list[Path] = []

  env_root = os.environ.get("AGENT_TASK_MANAGER_REPO_ROOT")
  if env_root:
    candidates.append(Path(env_root).expanduser())

  candidates.append(Path.cwd().resolve())
  candidates.extend(Path.cwd().resolve().parents)
  candidates.extend(script_root.parents)
  candidates.extend(
      [
          home / "workspace" / "AgentTaskManager",
          home / "src" / "AgentTaskManager",
          home / "dev" / "AgentTaskManager",
          Path("F:/workspace/AgentTaskManager"),
          Path("D:/workspace/AgentTaskManager"),
          Path("/srv/AgentTaskManager"),
      ]
  )
  return candidates


def resolve_repo_root() -> Path:
  for candidate in candidate_repo_roots():
    candidate = candidate.resolve()
    if has_repo_layout(candidate):
      return candidate
  raise RuntimeError(
      "Could not resolve the AgentTaskManager repo root. Set AGENT_TASK_MANAGER_REPO_ROOT."
  )


def resolve_java_command() -> str:
  java_home = os.environ.get("JAVA_HOME")
  if java_home:
    java_path = Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")
    if java_path.exists():
      return str(java_path)
  java_command = shutil.which("java")
  if java_command:
    return java_command
  raise RuntimeError("Could not find java. Set JAVA_HOME or add java to PATH.")


def resolve_maven_command() -> str | None:
  for command in ("mvn", "mvn.cmd", "mvn.bat"):
    resolved = shutil.which(command)
    if resolved:
      return resolved
  return None


def app_jar_candidates(repo_root: Path) -> list[Path]:
  target_dir = repo_root / "tavall-ai-app" / "target"
  if not target_dir.is_dir():
    return []
  candidates = [
      path
      for path in target_dir.glob("tavall-ai-app-*.jar")
      if not path.name.endswith(('-sources.jar', '-javadoc.jar', '-tests.jar', '-plain.jar'))
  ]
  return sorted(candidates, key=lambda item: item.stat().st_mtime, reverse=True)


def app_jar_path(repo_root: Path) -> Path | None:
  candidates = app_jar_candidates(repo_root)
  return candidates[0] if candidates else None


def ensure_app_jar(repo_root: Path) -> Path:
  jar_path = app_jar_path(repo_root)
  if jar_path and jar_path.is_file():
    return jar_path

  maven_command = resolve_maven_command()
  if not maven_command:
    raise RuntimeError(
        "Could not find mvn, mvn.cmd, or mvn.bat and the ATM app jar is missing."
    )

  subprocess.run(
      [maven_command, "-q", "-pl", "tavall-ai-app", "-am", "package"],
      cwd=repo_root,
      check=True,
  )
  jar_path = app_jar_path(repo_root)
  if not jar_path or not jar_path.is_file():
    raise RuntimeError("Expected ATM app jar after build, but it was not created.")
  return jar_path


def lock_path(repo_root: Path) -> Path:
  lock_dir = repo_root / ".tavall-ai"
  lock_dir.mkdir(parents=True, exist_ok=True)
  return lock_dir / "mcp-stdio.lock"


def _pid_exists(pid: int) -> bool:
  if pid <= 0:
    return False
  if os.name == "nt":
    result = subprocess.run(
        ["tasklist", "/FI", f"PID eq {pid}"],
        capture_output=True,
        text=True,
        check=False,
    )
    return str(pid) in result.stdout
  try:
    os.kill(pid, 0)
    return True
  except OSError:
    return False


def _pid_command_line(pid: int) -> str:
  if pid <= 0:
    return ""
  if os.name != "nt":
    return ""
  command = (
      f"(Get-CimInstance Win32_Process -Filter \"ProcessId={pid}\").CommandLine"
  )
  result = subprocess.run(
      ["powershell", "-NoProfile", "-Command", command],
      capture_output=True,
      text=True,
      check=False,
  )
  return result.stdout.strip()


def acquire_mcp_stdio_lock(repo_root: Path, jar_path: Path) -> None:
  lock_file = lock_path(repo_root)
  if lock_file.is_file():
    try:
      data = json.loads(lock_file.read_text(encoding="utf-8"))
    except Exception:
      data = {}

    pid = int(data.get("pid", 0) or 0)
    if _pid_exists(pid):
      cmdline = _pid_command_line(pid)
      if cmdline and ("tavall-ai-app" in cmdline or jar_path.name in cmdline):
        raise RuntimeError(
            f"tavall-ai MCP stdio is already running (pid={pid})."
        )
      if not cmdline:
        raise RuntimeError(
            f"tavall-ai MCP stdio may already be running (pid={pid})."
        )
    try:
      lock_file.unlink()
    except OSError:
      pass

  payload = {
      "pid": os.getpid(),
      "jar": str(jar_path),
      "started_at": int(time.time()),
  }
  lock_file.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def http_reachable(url: str) -> bool:
  request = urllib.request.Request(url, method="GET")
  try:
    with urllib.request.urlopen(request, timeout=5) as response:
      return response.status in {200, 302, 401, 403}
  except urllib.error.HTTPError as error:
    return error.code in {200, 302, 401, 403}
  except Exception:
    return False


def quoted_command(arguments: list[str]) -> str:
  return subprocess.list2cmdline(arguments)


def fail(message: str) -> int:
  print(message, file=sys.stderr)
  return 1


