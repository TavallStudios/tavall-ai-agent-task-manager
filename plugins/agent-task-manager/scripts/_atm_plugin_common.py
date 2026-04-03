from __future__ import annotations

import os
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path


def plugin_root() -> Path:
  return Path(__file__).resolve().parent.parent


def has_repo_layout(candidate: Path) -> bool:
  return (
      (candidate / "pom.xml").is_file()
      and (candidate / "agent-task-manager-app").is_dir()
      and (candidate / "agent-task-manager").is_dir()
  )


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


def app_jar_path(repo_root: Path) -> Path:
  return repo_root / "agent-task-manager-app" / "target" / "agent-task-manager-app-0.1.0-SNAPSHOT.jar"


def ensure_app_jar(repo_root: Path) -> Path:
  jar_path = app_jar_path(repo_root)
  if jar_path.is_file():
    return jar_path

  maven_command = resolve_maven_command()
  if not maven_command:
    raise RuntimeError(
        "Could not find mvn, mvn.cmd, or mvn.bat and the ATM app jar is missing."
    )

  subprocess.run(
      [maven_command, "-q", "-pl", "agent-task-manager-app", "-am", "package"],
      cwd=repo_root,
      check=True,
  )
  if not jar_path.is_file():
    raise RuntimeError(f"Expected ATM app jar at {jar_path} after build, but it was not created.")
  return jar_path


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
