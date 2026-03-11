#!/usr/bin/env python3
import argparse
import base64
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


IGNORABLE_STDERR = (
    "Failed to kill MCP process group",
    "Failed to delete shell snapshot",
    "Error reading from stream: serde error expected value",
    "failed to unwatch /home/ubuntu/.codex/skills/.system",
)

DEFAULT_HTTP_HEADERS = {
    "User-Agent": "curl/8.7.1",
    "Accept": "*/*",
}

LOCAL_PC_ROOT_PREFIX = "/srv/local-pc-root/"


def basic_auth(username: str, password: str) -> str:
    token = base64.b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
    return f"Basic {token}"


def post_json(base_url: str, path: str, payload: dict, auth_header: str) -> dict:
    url = urllib.parse.urljoin(base_url.rstrip("/") + "/", path.lstrip("/"))
    body = json.dumps(payload)
    curl_command = shutil.which("curl") or shutil.which("curl.exe")
    if curl_command:
        result = subprocess.run(
            [
                curl_command,
                "--silent",
                "--show-error",
                "--fail-with-body",
                "-X",
                "POST",
                url,
                "-H",
                f"Authorization: {auth_header}",
                "-H",
                "Content-Type: application/json",
                "-H",
                f"User-Agent: {DEFAULT_HTTP_HEADERS['User-Agent']}",
                "-H",
                f"Accept: {DEFAULT_HTTP_HEADERS['Accept']}",
                "--data-binary",
                body,
            ],
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        response_body = (result.stdout or "").strip()
        return json.loads(response_body) if response_body else {}

    request = urllib.request.Request(
        url,
        data=body.encode("utf-8"),
        headers={
            **DEFAULT_HTTP_HEADERS,
            "Authorization": auth_header,
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        body = response.read().decode("utf-8")
        return json.loads(body) if body else {}


def parse_event_messages(line: str) -> list[dict]:
    try:
      root = json.loads(line)
    except json.JSONDecodeError:
      return []

    event_type = root.get("type")
    messages = []
    if event_type == "thread.started":
        thread_id = root.get("thread_id", "")
        messages.append({
            "messageKind": "thread-started",
            "senderName": "codex-bridge",
            "body": f"Started thread {thread_id}",
            "threadSessionId": thread_id,
        })
    elif event_type == "turn.started":
        messages.append({
            "messageKind": "turn-started",
            "senderName": "codex-bridge",
            "body": "Codex started processing the prompt.",
        })
    elif event_type == "turn.completed":
        usage = root.get("usage") or {}
        messages.append({
            "messageKind": "usage",
            "senderName": "codex-bridge",
            "body": "input=%s cached=%s output=%s" % (
                usage.get("input_tokens", 0),
                usage.get("cached_input_tokens", 0),
                usage.get("output_tokens", 0),
            ),
        })
    elif event_type == "item.completed":
        item = root.get("item") or {}
        if item.get("type") == "agent_message":
            body = item.get("text") or ""
            if body:
                messages.append({
                    "messageKind": "agent-message",
                    "senderName": "codex",
                    "body": body,
                })
    return messages


def translate_repo_path(repo_path: str) -> str:
    normalized = (repo_path or "").replace("\\", "/")
    if os.name == "nt":
        if normalized.startswith(LOCAL_PC_ROOT_PREFIX):
            normalized = normalized[len(LOCAL_PC_ROOT_PREFIX):]
        if len(normalized) >= 2 and normalized[1] == ":":
            return normalized.replace("/", "\\")
    return repo_path


def build_command(args, claim: dict, output_file: str, repo_path: str) -> list[str]:
    command = [
        args.codex_command,
        "-C", repo_path,
        "-s", "read-only" if claim["executionMode"] == "read-only" else "workspace-write",
        "exec",
        # The current Windows Codex CLI does not support `exec resume` with the
        # JSON and output-file flags the bridge needs, so each queued prompt
        # starts a fresh Codex session even when the web thread already exists.
        "--skip-git-repo-check",
        "--json",
        "--output-last-message", output_file,
    ]
    command.append(claim["promptText"].strip())
    return command


def append_message(args, request_id: str, run_id: int, message: dict) -> None:
    payload = {
        "requestId": request_id,
        "messageKind": message["messageKind"],
        "senderName": message["senderName"],
        "body": message["body"],
    }
    if message.get("threadSessionId"):
        payload["threadSessionId"] = message["threadSessionId"]
    post_json(args.base_url, f"/api/bridge/runs/{run_id}/messages", payload, args.auth_header)


def complete_run(args, request_id: str, run_id: int, summary: str, thread_session_id: str | None) -> None:
    payload = {
        "requestId": request_id,
        "summary": summary,
    }
    if thread_session_id:
        payload["threadSessionId"] = thread_session_id
    post_json(args.base_url, f"/api/bridge/runs/{run_id}/complete", payload, args.auth_header)


def fail_run(args, request_id: str, run_id: int, exit_code: int, summary: str, thread_session_id: str | None) -> None:
    payload = {
        "requestId": request_id,
        "exitCode": exit_code,
        "summary": summary,
    }
    if thread_session_id:
        payload["threadSessionId"] = thread_session_id
    post_json(args.base_url, f"/api/bridge/runs/{run_id}/fail", payload, args.auth_header)


def stream_stderr(args, process: subprocess.Popen, request_id: str, run_id: int, thread_state: dict) -> None:
    assert process.stderr is not None
    for raw_line in process.stderr:
        line = raw_line.strip()
        if not line or any(fragment in line for fragment in IGNORABLE_STDERR):
            continue
        append_message(args, request_id, run_id, {
            "messageKind": "stderr",
            "senderName": "codex-bridge",
            "body": line,
            "threadSessionId": thread_state.get("threadSessionId"),
        })


def execute_claim(args, claim: dict) -> None:
    thread_state = {"threadSessionId": claim.get("resumeSessionId")}
    repo_path = translate_repo_path(claim["repoPath"])
    if not repo_path or not os.path.isdir(repo_path):
        fail_run(
            args,
            claim["requestId"],
            claim["runId"],
            1,
            f"Resolved repo path does not exist on bridge host: {repo_path or claim['repoPath']}",
            thread_state.get("threadSessionId"),
        )
        return

    with tempfile.NamedTemporaryFile(prefix="agent-task-manager-", suffix=".txt", delete=False) as handle:
        output_path = handle.name

    command = build_command(args, claim, output_path, repo_path)
    env = os.environ.copy()
    if args.codex_real_bin:
        env["CODEX_REAL_BIN"] = args.codex_real_bin

    process = subprocess.Popen(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        stdin=subprocess.DEVNULL,
        text=True,
        encoding="utf-8",
        env=env,
    )

    append_message(args, claim["requestId"], claim["runId"], {
        "messageKind": "bridge-status",
        "senderName": "local-ide-bridge",
        "body": f"Started local bridge run on {repo_path}",
        "threadSessionId": thread_state.get("threadSessionId"),
    })

    stderr_thread = threading.Thread(
        target=stream_stderr,
        args=(args, process, claim["requestId"], claim["runId"], thread_state),
        daemon=True,
    )
    stderr_thread.start()

    assert process.stdout is not None
    for raw_line in process.stdout:
        line = raw_line.strip()
        if not line:
            continue
        for message in parse_event_messages(line):
            if message.get("threadSessionId"):
                thread_state["threadSessionId"] = message["threadSessionId"]
            append_message(args, claim["requestId"], claim["runId"], message)

    exit_code = process.wait()
    stderr_thread.join(timeout=5)

    final_message = ""
    if os.path.exists(output_path):
      final_message = pathlib.Path(output_path).read_text(encoding="utf-8").strip()
      pathlib.Path(output_path).unlink(missing_ok=True)

    if final_message:
        append_message(args, claim["requestId"], claim["runId"], {
            "messageKind": "final-response",
            "senderName": "codex",
            "body": final_message,
            "threadSessionId": thread_state.get("threadSessionId"),
        })

    summary = final_message or f"Codex run completed with exit code {exit_code}"
    if final_message or exit_code == 0:
        complete_run(args, claim["requestId"], claim["runId"], summary, thread_state.get("threadSessionId"))
    else:
        fail_run(args, claim["requestId"], claim["runId"], exit_code, summary, thread_state.get("threadSessionId"))


def load_or_create_session_id(path: pathlib.Path) -> str:
    if path.exists():
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
            session_id = payload.get("sessionId")
            if session_id:
                return session_id
        except json.JSONDecodeError:
            pass
    session_id = "session-" + str(uuid.uuid4())
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"sessionId": session_id}, indent=2), encoding="utf-8")
    return session_id


def heartbeat(args) -> None:
    post_json(args.base_url, "/api/bridge/sessions/heartbeat", {
        "sessionId": args.session_id,
        "agentId": args.agent_id,
        "hostName": args.host_name,
        "clientName": args.client_name,
        "repoPath": args.repo_path,
        "status": "online",
        "capabilities": {
            "bridgeTarget": args.bridge_target,
            "resumeSupported": True,
            "transport": "tunnel",
        },
    }, args.auth_header)


def claim_next(args) -> dict | None:
    response = post_json(args.base_url, "/api/bridge/claims/next", {
        "sessionId": args.session_id,
        "agentId": args.agent_id,
        "hostName": args.host_name,
        "clientName": args.client_name,
        "repoPath": args.repo_path,
        "bridgeTarget": args.bridge_target,
        "capabilities": {
            "bridgeTarget": args.bridge_target,
            "resumeSupported": True,
            "transport": "tunnel",
        },
    }, args.auth_header)
    return response.get("claim")


def main() -> int:
    parser = argparse.ArgumentParser(description="AgentTaskManager local IDE bridge")
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--username", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--bridge-target", default="local-ide")
    parser.add_argument("--agent-id", default="local-ide-bridge")
    parser.add_argument("--client-name", default="Local Codex IDE Bridge")
    parser.add_argument("--repo-path", default="")
    parser.add_argument("--host-name", default=os.uname().nodename if hasattr(os, "uname") else "local-host")
    parser.add_argument("--poll-interval", type=float, default=5.0)
    parser.add_argument("--codex-command", default=os.environ.get("AGENT_TASK_MANAGER_LOCAL_CODEX_COMMAND", "codex"))
    parser.add_argument("--codex-real-bin", default=os.environ.get("CODEX_REAL_BIN", ""))
    parser.add_argument("--session-file", default=str(pathlib.Path.home() / ".agent-task-manager" / "local-bridge-session.json"))
    args = parser.parse_args()

    args.auth_header = basic_auth(args.username, args.password)
    args.session_id = load_or_create_session_id(pathlib.Path(args.session_file))

    post_json(args.base_url, "/api/bridge/sessions/register", {
        "sessionId": args.session_id,
        "agentId": args.agent_id,
        "hostName": args.host_name,
        "clientName": args.client_name,
        "repoPath": args.repo_path,
        "status": "online",
        "capabilities": {
            "bridgeTarget": args.bridge_target,
            "resumeSupported": True,
            "transport": "tunnel",
        },
    }, args.auth_header)

    while True:
        try:
            heartbeat(args)
            claim = claim_next(args)
            if claim:
                execute_claim(args, claim)
            else:
                time.sleep(args.poll_interval)
        except urllib.error.HTTPError as error:
            print(f"bridge http error: {error.code}", file=sys.stderr)
            time.sleep(args.poll_interval)
        except Exception as error:  # noqa: BLE001
            print(f"bridge failure: {error}", file=sys.stderr)
            time.sleep(args.poll_interval)


if __name__ == "__main__":
    raise SystemExit(main())
